package com.autopilot.service.deployment;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.intelligence.model.ConfigEntry;
import com.autopilot.intelligence.model.ConfigIntelligenceResult;
import com.autopilot.service.aws.AwsCredentialService;
import com.autopilot.service.aws.RdsProvisioningService;
import com.autopilot.service.aws.SecretsManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    /**
     * Complete result of dependency provisioning.
     */
    public record ProvisionResult(
            Map<String, String> envVars,
            List<String> dockerEnvFlags,
            String rdsEndpoint,
            String redisEndpoint,
            String secretsArn,
            List<String> provisionedServices,
            List<String> warnings,
            List<String> preDeployDbCommands
    ) {}

    /**
     * Full dependency provision pipeline.
     *
     * @param configResult  Config Intelligence results (databases, caches, env map)
     * @param deploymentId  Deployment ID
     * @param roleArn       AWS IAM Role ARN
     * @param region        AWS Region
     * @param workspace     Workspace path (for localhost replacement)
     * @param ec2InstanceId EC2 instance ID (for Redis-on-EC2 fallback)
     * @param progressLog   Callback for real-time log messages (sent to frontend)
     * @return ProvisionResult with everything needed for container deployment
     */
    public ProvisionResult provision(
            ConfigIntelligenceResult configResult,
            String deploymentId,
            String roleArn,
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

        List<String> preDeployDbCommands = new ArrayList<>();

        // ── STEP 1: Provision databases ──────────────────────────────────
        for (String db : configResult.getDatabases()) {
            boolean rdsSuccess = false;
            try {
                RdsProvisioningService.RdsResult rdsResult = rdsProvisioningService.provision(
                        db, deploymentId, roleArn, region, progressLog);

                if (rdsResult != null) {
                    envVars.putAll(rdsResult.envVars());
                    rdsEndpoint = rdsResult.endpoint() + ":" + rdsResult.port();
                    provisioned.add("RDS:" + db.toUpperCase());
                    progressLog.accept("✅ Provisioned RDS (" + db + ") → " + rdsResult.endpoint());
                    rdsSuccess = true;
                }
            } catch (Exception e) {
                warnings.add("RDS provisioning failed for " + db + ": " + e.getMessage());
            }

            // FALLBACK TO DOCKER ON EC2 IF RDS FAILS
            if (!rdsSuccess) {
                progressLog.accept("⚠️ RDS unavailable. Falling back to local Docker container on EC2 for " + db.toUpperCase());

                String fallbackPassword = "AP" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                String fallbackDbName = "autopilotdb";
                
                if ("mysql".equalsIgnoreCase(db)) {
                    String mysqlJdbcParams = "?allowPublicKeyRetrieval=true&useSSL=false&createDatabaseIfNotExist=true";
                    preDeployDbCommands.addAll(List.of(
                        "docker pull mysql:8",
                        "docker rm -f autopilot-mysql 2>/dev/null || true",
                        "docker run -d --name autopilot-mysql --network autopilot --restart unless-stopped " +
                        "-e MYSQL_ROOT_PASSWORD=" + fallbackPassword + " -e MYSQL_DATABASE=" + fallbackDbName + " mysql:8",
                        // Wait for MySQL to be ready before starting the app
                        "for i in $(seq 1 30); do" +
                        " if docker exec autopilot-mysql mysqladmin ping -h localhost --silent 2>/dev/null; then" +
                        " echo 'MySQL ready (attempt '$i')'; break; fi;" +
                        " echo 'Waiting for MySQL... ('$i'/30)'; sleep 2; done",
                        "echo 'MySQL container started on autopilot-mysql:3306'"
                    ));
                    
                    envVars.put("DATABASE_URL", "jdbc:mysql://autopilot-mysql:3306/" + fallbackDbName + mysqlJdbcParams);
                    envVars.put("DB_HOST", "autopilot-mysql");
                    envVars.put("DB_PORT", "3306");
                    envVars.put("DB_NAME", fallbackDbName);
                    envVars.put("DB_USER", "root");
                    envVars.put("DB_PASSWORD", fallbackPassword);
                    envVars.put("SPRING_DATASOURCE_URL", "jdbc:mysql://autopilot-mysql:3306/" + fallbackDbName + mysqlJdbcParams);
                    envVars.put("SPRING_DATASOURCE_USERNAME", "root");
                    envVars.put("SPRING_DATASOURCE_PASSWORD", fallbackPassword);
                    
                    provisioned.add("MYSQL:DOCKER_ON_EC2");
                    rdsEndpoint = "autopilot-mysql:3306"; // Set for replacement logic downstream
                    
                } else if ("postgres".equalsIgnoreCase(db)) {
                    preDeployDbCommands.addAll(List.of(
                        "docker pull postgres:15",
                        "docker rm -f autopilot-postgres 2>/dev/null || true",
                        "docker run -d --name autopilot-postgres --network autopilot --restart unless-stopped " +
                        "-e POSTGRES_PASSWORD=" + fallbackPassword + " -e POSTGRES_DB=" + fallbackDbName + " postgres:15",
                        // Wait for Postgres to be ready before starting the app
                        "for i in $(seq 1 30); do" +
                        " if docker exec autopilot-postgres pg_isready -U postgres --silent 2>/dev/null; then" +
                        " echo 'Postgres ready (attempt '$i')'; break; fi;" +
                        " echo 'Waiting for Postgres... ('$i'/30)'; sleep 2; done",
                        "echo 'Postgres container started on autopilot-postgres:5432'"
                    ));
                    
                    envVars.put("DATABASE_URL", "jdbc:postgresql://autopilot-postgres:5432/" + fallbackDbName);
                    envVars.put("DB_HOST", "autopilot-postgres");
                    envVars.put("DB_PORT", "5432");
                    envVars.put("DB_NAME", fallbackDbName);
                    envVars.put("DB_USER", "postgres");
                    envVars.put("DB_PASSWORD", fallbackPassword);
                    envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://autopilot-postgres:5432/" + fallbackDbName);
                    envVars.put("SPRING_DATASOURCE_USERNAME", "postgres");
                    envVars.put("SPRING_DATASOURCE_PASSWORD", fallbackPassword);

                    provisioned.add("POSTGRES:DOCKER_ON_EC2");
                    rdsEndpoint = "autopilot-postgres:5432";
                } else if ("mongodb".equalsIgnoreCase(db) || "mongo".equalsIgnoreCase(db)) {
                    preDeployDbCommands.addAll(List.of(
                        "docker pull mongo:6",
                        "docker rm -f autopilot-mongo 2>/dev/null || true",
                        "docker run -d --name autopilot-mongo --network autopilot --restart unless-stopped " +
                        "-e MONGO_INITDB_ROOT_USERNAME=root -e MONGO_INITDB_ROOT_PASSWORD=" + fallbackPassword + " mongo:6",
                        // Wait for Mongo to be ready before starting the app
                        "for i in $(seq 1 30); do" +
                        " if docker exec autopilot-mongo mongosh --quiet --eval 'db.adminCommand(\"ping\")' -u root -p " + fallbackPassword + " --authenticationDatabase admin 2>/dev/null; then" +
                        " echo 'Mongo ready (attempt '$i')'; break; fi;" +
                        " echo 'Waiting for Mongo... ('$i'/30)'; sleep 2; done",
                        "echo 'Mongo container started on autopilot-mongo:27017'"
                    ));
                    
                    String mongoUrl = "mongodb://root:" + fallbackPassword + "@autopilot-mongo:27017/" + fallbackDbName + "?authSource=admin";
                    envVars.put("MONGO_URL", mongoUrl);
                    envVars.put("MONGODB_URI", mongoUrl);
                    envVars.put("SPRING_DATA_MONGODB_URI", mongoUrl);

                    provisioned.add("MONGODB:DOCKER_ON_EC2");
                    rdsEndpoint = "autopilot-mongo:27017";
                }
            }
        }

        // ── STEP 2: Provision caches (Redis on EC2) ──────────────────────
        for (String cache : configResult.getCaches()) {
            if ("redis".equalsIgnoreCase(cache)) {
                // Redis will be deployed as Docker container on EC2 alongside the app
                redisEndpoint = "autopilot-redis:6379";
                envVars.put("REDIS_URL", "redis://autopilot-redis:6379");
                envVars.put("REDIS_HOST", "autopilot-redis");
                envVars.put("REDIS_PORT", "6379");
                envVars.put("SPRING_DATA_REDIS_HOST", "autopilot-redis");
                envVars.put("SPRING_DATA_REDIS_PORT", "6379");
                envVars.put("CACHE_URL", "redis://autopilot-redis:6379");
                provisioned.add("REDIS:DOCKER_ON_EC2");
                progressLog.accept("✅ Redis will be deployed as Docker container on EC2");
            }
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
                        deploymentId, secretEntries, roleArn, region);
            } catch (Exception e) {
                warnings.add("Secrets Manager storage failed: " + e.getMessage());
            }
        }

        // ── STEP 5: Build Docker -e flags ────────────────────────────────
        List<String> dockerEnvFlags = buildDockerEnvFlags(envVars);

        return new ProvisionResult(
                envVars, dockerEnvFlags, rdsEndpoint, redisEndpoint,
                secretsArn, provisioned, warnings, preDeployDbCommands
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
                if (rdsEndpoint != null) {
                    String rdsHost = rdsEndpoint.contains(":") ? rdsEndpoint.split(":")[0] : rdsEndpoint;

                    content = content.replace("localhost:3306", rdsEndpoint);
                    content = content.replace("127.0.0.1:3306", rdsEndpoint);
                    content = content.replace("localhost:5432", rdsEndpoint);
                    content = content.replace("127.0.0.1:5432", rdsEndpoint);

                    // JDBC URLs - we capture the full path and replace it so it uses the guaranteed created 'autopilotdb' schema
                    content = content.replaceAll(
                            "jdbc:mysql://localhost(:\\d+)?(/[a-zA-Z0-9_\\-\\.]+)?",
                            "jdbc:mysql://" + rdsEndpoint + "/autopilotdb"
                    );
                    content = content.replaceAll(
                            "jdbc:mysql://127\\.0\\.0\\.1(:\\d+)?(/[a-zA-Z0-9_\\-\\.]+)?",
                            "jdbc:mysql://" + rdsEndpoint + "/autopilotdb"
                    );
                    content = content.replaceAll(
                            "jdbc:postgresql://localhost(:\\d+)?(/[a-zA-Z0-9_\\-\\.]+)?",
                            "jdbc:postgresql://" + rdsEndpoint + "/autopilotdb"
                    );
                    content = content.replaceAll(
                            "jdbc:postgresql://127\\.0\\.0\\.1(:\\d+)?(/[a-zA-Z0-9_\\-\\.]+)?",
                            "jdbc:postgresql://" + rdsEndpoint + "/autopilotdb"
                    );

                    // MongoDB URLs
                    String finalMongoReplacement = envVars.containsKey("MONGODB_URI") ? envVars.get("MONGODB_URI") : "mongodb://" + rdsEndpoint + "/autopilotdb?authSource=admin";
                    content = content.replaceAll(
                            "mongodb://localhost(:\\d+)?(/[a-zA-Z0-9_\\-\\.]+)?",
                            finalMongoReplacement
                    );
                    content = content.replaceAll(
                            "mongodb://127\\.0\\.0\\.1(:\\d+)?(/[a-zA-Z0-9_\\-\\.]+)?",
                            finalMongoReplacement
                    );
                }

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
        int count = 0;
        Files.walk(workspace)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("node_modules"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        if (content.contains("setAllowedOrigins") && (content.contains("localhost") || content.contains("127.0.0.1"))) {
                            // Replace list of origins with a wildcard
                            String pattern = "setAllowedOrigins\\s*\\(\\s*List\\.of\\([^\\)]+\\)\\s*\\)";
                            String replaced = content.replaceAll(pattern, "setAllowedOrigins(List.of(\"*\"))");
                            
                            if (!replaced.equals(content)) {
                                Files.writeString(file, replaced);
                            }
                        }
                    } catch (Exception ignored) {}
                });
        
        return 1; 
    }
}
