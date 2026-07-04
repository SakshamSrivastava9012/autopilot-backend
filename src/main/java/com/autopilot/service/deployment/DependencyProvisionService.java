package com.autopilot.service.deployment;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.intelligence.model.ConfigEntry;
import com.autopilot.intelligence.model.ConfigIntelligenceResult;
import com.autopilot.service.aws.RdsProvisioningService;
import com.autopilot.service.aws.SecretsManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.autopilot.service.deployment.runtime.dependency.*;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Unified Dependency Provision + Auto-Link Service.
 *
 * Orchestrates the full lifecycle:
 *   1. Provision dependencies (RDS, Redis-on-EC2)
 *   2. Generate connection strings
 *   3. Store credentials in AWS Secrets Manager
 *   4. Build env var map with multi-injection
 *   5. Replace localhost references in config files
 *   6. Validate connections before deploy
 *   7. Build docker run -e flags
 */
@Service
@RequiredArgsConstructor
public class DependencyProvisionService {

    private final RdsProvisioningService rdsProvisioningService;
    private final SecretsManagerService secretsManagerService;
    private final ServiceDependencyEngine serviceDependencyEngine;

    /**
     * Complete result of dependency provisioning.
     */
    public record ProvisionResult(
            Map<String, String> envVars,
            List<String> dockerEnvFlags,
            String rdsEndpoint,
            String redisEndpoint,
            String secretsArn,
            String rdsSecurityGroupId,
            List<String> provisionedServices,
            List<String> warnings,
            List<String> preDeployDbCommands
    ) {}

    /**
     * Full dependency provision pipeline.
     *
     * @param configResult  Config Intelligence results (databases, caches, env map)
     * @param deploymentId  Deployment ID
     * @param creds         AWS Credentials DTO (resolved by CredentialResolverService)
     * @param region        AWS Region
     * @param workspace     Workspace path (for localhost replacement)
     * @param ec2InstanceId EC2 instance ID (for Redis-on-EC2 fallback)
     * @param progressLog   Callback for real-time log messages (sent to frontend)
     * @return ProvisionResult with everything needed for container deployment
     */
    public ProvisionResult provision(
            ConfigIntelligenceResult configResult,
            String deploymentId,
            AwsCredentialsDto creds,
            String region,
            Path workspace,
            String ec2InstanceId,
            Consumer<String> progressLog
    ) {
        Map<String, String> envVars = new LinkedHashMap<>(configResult.getEnvMap());
        List<String> provisioned = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String rdsEndpoint = null;
        String redisEndpoint = null;
        String rdsSecurityGroupId = null;

        List<String> preDeployDbCommands = new ArrayList<>();

        // ── STEP 1 & 2: Provision & negotiate databases & caches via V4.4 Engine ──
        try {
            RuntimeDependencyContract dependencyContract = serviceDependencyEngine.orchestrate(
                    configResult.getDatabases(),
                    configResult.getCaches(),
                    configResult.getEnvMap(),
                    (creds != null) ? "MANAGED" : "BYOC",
                    deploymentId,
                    creds,
                    region,
                    ec2InstanceId,
                    progressLog
            );
            
            envVars.putAll(dependencyContract.getNegotiatedEnvVars());
            preDeployDbCommands.addAll(dependencyContract.getPreDeployCommands());
            
            for (DependencyDescriptor desc : dependencyContract.getDependencies()) {
                provisioned.add(desc.getProvider() + ":" + desc.getType().toUpperCase());
                if (desc.getConnectionUri() != null) {
                    String cleanUri = desc.getConnectionUri();
                    if (cleanUri.startsWith("jdbc:")) {
                        cleanUri = cleanUri.substring(5);
                    }
                    try {
                        java.net.URI u = new java.net.URI(cleanUri);
                        if ("redis".equalsIgnoreCase(desc.getType())) {
                            redisEndpoint = u.getHost() + ":" + (u.getPort() != -1 ? u.getPort() : 6379);
                        } else {
                            rdsEndpoint = u.getHost() + ":" + (u.getPort() != -1 ? u.getPort() : 3306);
                        }
                    } catch (Exception ignored) {
                        if ("redis".equalsIgnoreCase(desc.getType())) {
                            redisEndpoint = "autopilot-redis:6379";
                        } else {
                            rdsEndpoint = "autopilot-" + desc.getType().toLowerCase() + (desc.getType().equalsIgnoreCase("mysql") ? ":3306" : ":5432");
                        }
                    }
                }
            }
        } catch (CredentialValidationException e) {
            DependencyReports.CredentialValidationReport rep = e.getReport();
            progressLog.accept("❌ Database Credential Validation Failed: " + rep.getFailureType());
            progressLog.accept("❌ Step: " + rep.getValidationStep());
            progressLog.accept("❌ Cause: " + rep.getRootCause());
            progressLog.accept("❌ Fix: " + rep.getSuggestedFix());
            throw new RuntimeException("DATABASE_VALIDATION_FAILED: " + rep.getFailureType() + " - " + rep.getRootCause());
        }

        // ── STEP 3: Replace localhost references in config files ─────────
        try {
            int replaced = replaceLocalhostReferences(workspace, envVars, rdsEndpoint, redisEndpoint);
            if (replaced > 0) {
                progressLog.accept("🔄 Replaced localhost references in " + replaced + " files");
            }
        } catch (Exception e) {
            warnings.add("Localhost replacement failed: " + e.getMessage());
        }

        // ── STEP 3b: Neutralize hardcoded AWS credentials in config files ──
        // Replace hardcoded IAM credentials with instance-profile usage so the
        // deployed app uses EC2 instance metadata for AWS auth (S3, SQS, etc.)
        try {
            int awsFixed = neutralizeHardcodedAwsCredentials(workspace);
            if (awsFixed > 0) {
                progressLog.accept("🔒 Replaced hardcoded AWS credentials in " + awsFixed + " config files → instance-profile");
            }
        } catch (Exception e) {
            warnings.add("AWS credential neutralization failed: " + e.getMessage());
        }

        // ── STEP 3c: Neutralize CORS settings ──
        try {
            int corsFixed = neutralizeCorsSettings(workspace);
            if (corsFixed > 0) {
                progressLog.accept("🌍 Relaxed CORS origins in " + corsFixed + " security files to allow deployment access");
            }
        } catch (Exception e) {
            warnings.add("CORS neutralization failed: " + e.getMessage());
        }

        // ── STEP 4: Store secrets in AWS Secrets Manager ─────────────────
        String secretsArn = null;
        List<ConfigEntry> secretEntries = configResult.getEntries().stream()
                .filter(ConfigEntry::isSecret)
                .collect(Collectors.toList());

        // Also add RDS credentials as secrets
        if (rdsEndpoint != null && envVars.containsKey("DB_PASSWORD")) {
            secretEntries.add(ConfigEntry.builder()
                    .key("DB_PASSWORD").value(envVars.get("DB_PASSWORD"))
                    .normalizedKey("DB_PASSWORD").secret(true).sourceFile("auto-provisioned").build());
        }

        if (!secretEntries.isEmpty()) {
            try {
                secretsArn = secretsManagerService.storeSecrets(
                        deploymentId, secretEntries, creds, region);
            } catch (Exception e) {
                warnings.add("Secrets Manager storage failed: " + e.getMessage());
            }
        }

        // ── STEP 5: Build Docker -e flags ────────────────────────────────
        List<String> dockerEnvFlags = buildDockerEnvFlags(envVars);

        return new ProvisionResult(
                envVars, dockerEnvFlags, rdsEndpoint, redisEndpoint,
                secretsArn, rdsSecurityGroupId, provisioned, warnings, preDeployDbCommands
        );
    }

    /**
     * Build the SSM docker run commands to provision Redis on EC2.
     * Returns the shell commands needed to start a Redis container.
     */
    public List<String> buildRedisProvisionCommands() {
        return List.of(
                "docker pull redis:7-alpine",
                "docker rm -f autopilot-redis 2>/dev/null || true",
                "docker run -d --name autopilot-redis --network autopilot --restart unless-stopped redis:7-alpine",
                "echo 'Redis container started on autopilot-redis:6379'"
        );
    }

    /**
     * Validate that provisioned dependencies are actually reachable.
     *
     * @return true if all connections valid, false if any failed
     */
    public boolean validateConnections(String rdsEndpoint, String redisEndpoint) {
        boolean allValid = true;

        if (rdsEndpoint != null && !rdsEndpoint.isBlank()) {
            String[] parts = rdsEndpoint.split(":");
            if (parts.length == 2) {
                boolean reachable = checkTcpConnection(parts[0], Integer.parseInt(parts[1]), 10_000);
                if (!reachable) {
                    System.err.println("⚠️ RDS endpoint not reachable: " + rdsEndpoint);
                    allValid = false;
                } else {
                    System.out.println("✅ RDS connection validated: " + rdsEndpoint);
                }
            }
        }

        // Redis on EC2 is validated after deploy (it's on the same host)
        return allValid;
    }

    /**
     * TCP connection check with timeout.
     */
    private boolean checkTcpConnection(String host, int port, int timeoutMs) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
                return true;
            } catch (Exception e) {
                System.out.println("   TCP check attempt " + attempt + "/3 failed for " + host + ":" + port);
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }

    /**
     * Replace localhost/127.0.0.1 references in config files with actual endpoints.
     */
    private int replaceLocalhostReferences(
            Path workspace, Map<String, String> envVars,
            String rdsEndpoint, String redisEndpoint
    ) throws IOException {
        if (rdsEndpoint == null && redisEndpoint == null) return 0;

        int count = 0;
        Set<String> configExtensions = Set.of(
                ".properties", ".yml", ".yaml", ".env", ".json", ".toml", ".cfg", ".ini", ".conf"
        );

        List<Path> configFiles = Files.walk(workspace)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return configExtensions.stream().anyMatch(name::endsWith) || name.equals(".env");
                })
                .filter(p -> !p.toString().contains("node_modules"))
                .filter(p -> !p.toString().contains(".git/"))
                .collect(Collectors.toList());

        for (Path file : configFiles) {
            try {
                String content = Files.readString(file);
                String original = content;

                    // Replace database localhost references
                    // MySQL / MariaDB
                    java.util.regex.Pattern mysqlPattern = java.util.regex.Pattern.compile(
                            "jdbc:mysql://(localhost|127\\.0\\.0\\.1)(:\\d+)?(/([a-zA-Z0-9_\\-\\.]+))?(\\?[^\\s\"'\\n]*)?"
                    );
                    java.util.regex.Matcher mysqlMatcher = mysqlPattern.matcher(content);
                    StringBuilder mysqlSb = new StringBuilder();
                    while (mysqlMatcher.find()) {
                        String dbName = mysqlMatcher.group(4);
                        String queryParams = mysqlMatcher.group(5);
                        String replacement = "jdbc:mysql://" + rdsEndpoint + 
                                "/" + (dbName != null ? dbName : "autopilotdb") + 
                                (queryParams != null ? queryParams : "");
                        mysqlMatcher.appendReplacement(mysqlSb, java.util.regex.Matcher.quoteReplacement(replacement));
                    }
                    mysqlMatcher.appendTail(mysqlSb);
                    content = mysqlSb.toString();

                    // PostgreSQL
                    java.util.regex.Pattern pgPattern = java.util.regex.Pattern.compile(
                            "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)(:\\d+)?(/([a-zA-Z0-9_\\-\\.]+))?(\\?[^\\s\"'\\n]*)?"
                    );
                    java.util.regex.Matcher pgMatcher = pgPattern.matcher(content);
                    StringBuilder pgSb = new StringBuilder();
                    while (pgMatcher.find()) {
                        String dbName = pgMatcher.group(4);
                        String queryParams = pgMatcher.group(5);
                        String replacement = "jdbc:postgresql://" + rdsEndpoint + 
                                "/" + (dbName != null ? dbName : "autopilotdb") + 
                                (queryParams != null ? queryParams : "");
                        pgMatcher.appendReplacement(pgSb, java.util.regex.Matcher.quoteReplacement(replacement));
                    }
                    pgMatcher.appendTail(pgSb);
                    content = pgSb.toString();

                    // MongoDB URLs
                    java.util.regex.Pattern mongoPattern = java.util.regex.Pattern.compile(
                            "mongodb://(localhost|127\\.0\\.0\\.1)(:\\d+)?(/([a-zA-Z0-9_\\-\\.]+))?(\\?[^\\s\"'\\n]*)?"
                    );
                    java.util.regex.Matcher mongoMatcher = mongoPattern.matcher(content);
                    StringBuilder mongoSb = new StringBuilder();
                    while (mongoMatcher.find()) {
                        String dbName = mongoMatcher.group(4);
                        String queryParams = mongoMatcher.group(5);
                        String replacement;
                        if (envVars.containsKey("MONGODB_URI")) {
                            replacement = envVars.get("MONGODB_URI");
                        } else {
                            replacement = "mongodb://" + rdsEndpoint + 
                                    "/" + (dbName != null ? dbName : "autopilotdb") + 
                                    (queryParams != null ? queryParams : "?authSource=admin");
                        }
                        mongoMatcher.appendReplacement(mongoSb, java.util.regex.Matcher.quoteReplacement(replacement));
                    }
                    mongoMatcher.appendTail(mongoSb);
                    content = mongoSb.toString();

                    // Literal string replacements (safe fallbacks for other config properties)
                    content = content.replace("localhost:3306", rdsEndpoint);
                    content = content.replace("127.0.0.1:3306", rdsEndpoint);
                    content = content.replace("localhost:5432", rdsEndpoint);
                    content = content.replace("127.0.0.1:5432", rdsEndpoint);

                if (!content.equals(original)) {
                    Files.writeString(file, content);
                    count++;
                    System.out.println("   🔄 Replaced localhost → production endpoint in " + workspace.relativize(file));
                }
            } catch (Exception ignored) {}
        }

        return count;
    }

    /**
     * Build Docker -e flags from environment map.
     * Filters out empty/placeholder values.
     */
    private List<String> buildDockerEnvFlags(Map<String, String> envVars) {
        List<String> flags = new ArrayList<>();
        for (var entry : envVars.entrySet()) {
            String value = entry.getValue();
            if (value != null && !value.isBlank() && !value.contains("placeholder")) {
                // Shell-safe escaping
                String escaped = value.replace("'", "'\\''");
                flags.add("-e " + entry.getKey() + "='" + escaped + "'");
            }
        }
        return flags;
    }

    /**
     * Scan config files for hardcoded AWS IAM credentials and replace them with
     * instance-profile usage. This ensures the deployed app uses EC2 instance
     * metadata for all AWS service calls (S3, SQS, DynamoDB, etc.)
     *
     * Handles:
     * - cloud.aws.credentials.access-key=AKIA...
     * - cloud.aws.credentials.secret-key=...
     * - cloud.aws.credentials.accessKey=...
     * - aws.accessKeyId=...
     *
     * Replaces with:
     * - cloud.aws.credentials.instance-profile=true
     * - cloud.aws.credentials.use-default-aws-credentials-chain=true
     */
    private int neutralizeHardcodedAwsCredentials(Path workspace) throws IOException {
        int count = 0;
        Set<String> configExtensions = Set.of(".properties", ".yml", ".yaml");

        List<Path> configFiles = Files.walk(workspace)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return configExtensions.stream().anyMatch(name::endsWith);
                })
                .filter(p -> !p.toString().contains("node_modules"))
                .filter(p -> !p.toString().contains(".git/"))
                .collect(Collectors.toList());

        // Patterns to match AWS credential lines in properties/yaml files
        // Covers: Spring Cloud AWS 2.x, 3.x, and raw AWS SDK patterns
        java.util.regex.Pattern awsCredPattern = java.util.regex.Pattern.compile(
                "(?m)^\\s*(" +
                // Spring Cloud AWS 2.x
                "cloud\\.aws\\.credentials\\.(access-key|secret-key|accessKey|secretKey)" +
                // Spring Cloud AWS 3.x (Spring Boot 3.x)
                "|spring\\.cloud\\.aws\\.credentials\\.(access-key|secret-key|accessKey|secretKey)" +
                "|spring\\.cloud\\.aws\\.credentials\\.static-credentials\\.(access-key|secret-key)" +
                // Raw AWS SDK properties
                "|aws\\.(accessKeyId|secretAccessKey|secretKey)" +
                // Duplicates for safety (catch-all)
                "|cloud\\.aws\\.credentials\\.access-key" +
                "|cloud\\.aws\\.credentials\\.secret-key" +
                // YAML nested keys (e.g., "  access-key: AKIA...")
                "|\\s*access-key\\s*:" +
                "|\\s*secret-key\\s*:" +
                ")\\s*[=:].*$"
        );

        for (Path file : configFiles) {
            try {
                String content = Files.readString(file);
                String original = content;

                if (awsCredPattern.matcher(content).find()) {
                    // Comment out hardcoded credential lines
                    content = awsCredPattern.matcher(content).replaceAll("# $0 # NEUTRALIZED BY AUTOPILOT — using instance-profile instead");

                    // Add instance-profile settings if not already present
                    if (!content.contains("instance-profile=true") && file.toString().endsWith(".properties")) {
                        content += "\n# Autopilot: Use EC2 instance role credentials instead of hardcoded keys\n";
                        content += "cloud.aws.credentials.instance-profile=true\n";
                        content += "cloud.aws.credentials.use-default-aws-credentials-chain=true\n";
                    }

                    if (!content.equals(original)) {
                        Files.writeString(file, content);
                        count++;
                        System.out.println("   🔒 Neutralized AWS credentials in " + workspace.relativize(file));
                    }
                }
            } catch (Exception ignored) {}
        }

        return count;
    }
    /**
     * Neutralize CORS origins in security configuration files.
     * Replaces specific origins (like localhost) with wildcard '*' to ensure
     * the deployed app is accessible from the dynamic EC2 IP.
     */
    private int neutralizeCorsSettings(Path workspace) throws IOException {
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);

        Files.walk(workspace)
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains("node_modules"))
                .filter(p -> !p.toString().contains(".git/"))
                .forEach(file -> {
                    try {
                        String name = file.getFileName().toString();
                        String content = Files.readString(file);
                        String original = content;

                        if (name.endsWith(".java")) {
                            // Fix setAllowedOrigins(List.of("localhost"...))
                            if (content.contains("setAllowedOrigins") && (content.contains("localhost") || content.contains("127.0.0.1"))) {
                                content = content.replaceAll(
                                        "setAllowedOrigins\\s*\\(\\s*List\\.of\\([^)]+\\)\\s*\\)",
                                        "setAllowedOrigins(List.of(\"*\"))");
                                content = content.replaceAll(
                                        "setAllowedOrigins\\s*\\(\\s*Arrays\\.asList\\([^)]+\\)\\s*\\)",
                                        "setAllowedOrigins(Arrays.asList(\"*\"))");
                            }
                            // Fix @CrossOrigin(origins = "http://localhost:3000")
                            content = content.replaceAll(
                                    "@CrossOrigin\\s*\\(\\s*origins\\s*=\\s*\"[^\"]*localhost[^\"]*\"\\s*\\)",
                                    "@CrossOrigin(origins = \"*\")");
                            // Fix @CrossOrigin(origins = {"http://...", "http://..."})
                            content = content.replaceAll(
                                    "@CrossOrigin\\s*\\(\\s*origins\\s*=\\s*\\{[^}]*localhost[^}]*\\}\\s*\\)",
                                    "@CrossOrigin(origins = \"*\")");
                        }

                        if (name.endsWith(".properties")) {
                            // Fix allowed-origins=http://localhost:3000
                            content = content.replaceAll(
                                    "(allowed-origins\\s*=).*localhost.*",
                                    "$1*");
                        }

                        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                            // Fix allowed-origins: http://localhost:3000
                            content = content.replaceAll(
                                    "(allowed-origins:\\s*).*localhost.*",
                                    "$1\"*\"");
                        }

                        if (!content.equals(original)) {
                            Files.writeString(file, content);
                            count.incrementAndGet();
                        }
                    } catch (Exception ignored) {}
                });

        return count.get();
    }
}
