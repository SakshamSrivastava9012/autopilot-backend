package com.autopilot.service.infrastructure.ec2;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.service.deployment.validation.StrategyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SSMDeployService {

    private final StrategyResolver strategyResolver;

    // SSM command timeout in seconds — Java poller must be LONGER than this
    // so SSM always finishes or times out before Java gives up waiting.
    private static final int CMD_TIMEOUT_SECONDS = 480;

    // Java poll iterations × interval must exceed CMD_TIMEOUT_SECONDS
    // 150 × 4s = 600s = 10 min > 480s SSM timeout — safe margin
    private static final int POLL_ITERATIONS = 150;
    private static final int POLL_INTERVAL_MS = 4000;

    /** No-op logger for backward compat */
    private static final Consumer<String> NOOP_LOG = msg -> {};

    // =========================
    // DEPLOY CONTAINER (legacy overload)
    // =========================
    public void deployContainer(
            String instanceId,
            String image,
            int hostPort,
            int containerPort,
            String region,
            AwsCredentialsDto creds,
            String deploymentId
    ) throws Exception {
        deployContainer(instanceId, image, hostPort, containerPort, region, creds, deploymentId, List.of(), List.of(), NOOP_LOG, deploymentId, "generic", "/", "HTTP", List.of(200, 204, 301, 302, 404), 60, 20);
    }

    // =========================
    // DEPLOY CONTAINER (env vars, no progress log)
    // =========================
    public void deployContainer(
            String instanceId,
            String image,
            int hostPort,
            int containerPort,
            String region,
            AwsCredentialsDto creds,
            String deploymentId,
            List<String> envFlags,
            List<String> preDeployCommands
    ) throws Exception {
        deployContainer(instanceId, image, hostPort, containerPort, region, creds, deploymentId, envFlags, preDeployCommands, NOOP_LOG, deploymentId, "generic", "/", "HTTP", List.of(200, 204, 301, 302, 404), 60, 20);
    }

    public void deployContainer(
            String instanceId,
            String image,
            int hostPort,
            int containerPort,
            String region,
            AwsCredentialsDto creds,
            String deploymentId,
            List<String> envFlags,
            List<String> preDeployCommands,
            Consumer<String> progressLog
    ) throws Exception {
        deployContainer(instanceId, image, hostPort, containerPort, region, creds, deploymentId, envFlags, preDeployCommands, progressLog, deploymentId, "generic", "/", "HTTP", List.of(200, 204, 301, 302, 404), 60, 20);
    }

    /**
     * Deploy container WITH environment variable injection, pre-deploy commands, and progress logging.
     */
    public static class DeploymentStep {
        public final String name;
        public final List<String> commands;
        public final int timeoutSeconds;

        public boolean isWaitStep = false;
        public String containerName;
        public int containerPort;
        public int hostPort;
        public String healthPath;
        public String protocol;
        public List<Integer> expectedStatusCodes;
        public String framework;
        public int startupTimeout;

        public DeploymentStep(String name, List<String> commands, int timeoutSeconds) {
            this.name = name;
            this.commands = commands;
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public void deployContainer(
            String instanceId,
            String image,
            int hostPort,
            int containerPort,
            String region,
            AwsCredentialsDto creds,
            String deploymentId,
            List<String> envFlags,
            List<String> preDeployCommands,
            Consumer<String> progressLog,
            String serviceId,
            String framework,
            String healthPath,
            String protocol,
            List<Integer> expectedStatusCodes,
            int startupTimeout,
            int retryPolicy
    ) throws Exception {
        com.autopilot.service.deployment.runtime.dependency.RuntimeContainerDescriptor fallbackDescriptor =
            new com.autopilot.service.deployment.runtime.dependency.RuntimeContainerDescriptor(
                "autopilot-" + deploymentId,
                "autopilot-mysql",
                "autopilot",
                hostPort,
                3306
            );
        deployContainer(instanceId, image, hostPort, containerPort, region, creds, deploymentId, envFlags,
            preDeployCommands, progressLog, serviceId, framework, healthPath, protocol, expectedStatusCodes,
            startupTimeout, retryPolicy, fallbackDescriptor);
    }

    public void deployContainer(
            String instanceId,
            String image,
            int hostPort,
            int containerPort,
            String region,
            AwsCredentialsDto creds,
            String deploymentId,
            List<String> envFlags,
            List<String> preDeployCommands,
            Consumer<String> progressLog,
            String serviceId,
            String framework,
            String healthPath,
            String protocol,
            List<Integer> expectedStatusCodes,
            int startupTimeout,
            int retryPolicy,
            com.autopilot.service.deployment.runtime.dependency.RuntimeContainerDescriptor descriptor
    ) throws Exception {

        SsmClient ssmClient = buildSsmClient(creds, region);

        progressLog.accept("📡 Waiting for SSM agent to come online on " + instanceId + "...");
        waitForSSM(ssmClient, instanceId, progressLog);
        progressLog.accept("✅ SSM agent online — sending deploy commands...");

        String registry      = image.substring(0, image.indexOf('/'));
        String containerName = (descriptor != null) ? descriptor.applicationContainerName() : ("autopilot-" + deploymentId);

        if (descriptor != null) {
            String expectedName = descriptor.applicationContainerName();
            if (!containerName.equals(expectedName)) {
                throw new IllegalStateException("Assertion Failed: Container name mismatch during launch! " +
                        "Launched container name: '" + containerName + "', but descriptor expects: '" + expectedName + "'");
            }
        }

        // Deduplicate and validate environment variables before docker run
        List<String> dedupedFlags = buildDeduplicatedEnvFlags(envFlags, null);
        checkForDuplicateEnvKeys(dedupedFlags);

        // Build the docker run command with env vars
        StringBuilder dockerRunCmd = new StringBuilder();
        dockerRunCmd.append(" docker run -d");
        dockerRunCmd.append(" --name ").append(containerName);
        dockerRunCmd.append(" --network autopilot");
        dockerRunCmd.append(" --restart unless-stopped");
        dockerRunCmd.append(" -p 127.0.0.1:").append(hostPort).append(":").append(containerPort);

        // Inject environment variables
        for (String flag : dedupedFlags) {
            dockerRunCmd.append(" ").append(flag);
        }

        dockerRunCmd.append(" ").append(image);
        validateDockerRunCommand(dockerRunCmd.toString());
        dockerRunCmd.append(" || { echo 'docker run failed. Container logs:'; docker logs ")
                .append(containerName).append(" 2>&1 || true; exit 1; }");

        // Startup verification loop
        String verifierScript = buildStartupVerifierScript(
                serviceId,
                framework,
                containerName,
                containerPort,
                hostPort,
                healthPath,
                protocol,
                expectedStatusCodes,
                startupTimeout,
                retryPolicy,
                dockerRunCmd.toString()
        );

        // Build Step-Aware Execution List
        List<DeploymentStep> steps = new java.util.ArrayList<>();

        // STEP 1: docker login
        List<String> loginCmds = new java.util.ArrayList<>();
        loginCmds.add("systemctl daemon-reload 2>/dev/null || true");
        loginCmds.add("systemctl enable docker 2>/dev/null || true");
        loginCmds.add("systemctl start docker 2>/dev/null || true");
        loginCmds.add("for i in $(seq 1 30); do if docker info >/dev/null 2>&1; then echo \"Docker ready (attempt $i)\"; break; fi; if [ $i -eq 30 ]; then echo 'ERROR: dockerd never became ready'; exit 1; fi; sleep 2; done");
        loginCmds.add("aws ecr get-login-password --region " + region + " | docker login --username AWS --password-stdin " + registry);
        steps.add(new DeploymentStep("docker login", loginCmds, 120));

        // Parse preDeployCommands to extract dependency steps
        List<String> depPulls = new java.util.ArrayList<>();
        List<String> depStarts = new java.util.ArrayList<>();
        List<String> depWaits = new java.util.ArrayList<>();

        for (int idx = 0; idx < preDeployCommands.size(); idx++) {
            String cmd = preDeployCommands.get(idx);
            if (cmd.startsWith("docker pull ")) {
                depPulls.add(cmd);
            } else if (cmd.startsWith("docker rm -f ")) {
                depStarts.add(cmd);
                if (idx + 1 < preDeployCommands.size() && preDeployCommands.get(idx + 1).contains("docker run ")) {
                    depStarts.add(preDeployCommands.get(idx + 1));
                    idx++;
                }
            } else if (cmd.contains("DEBUG: Starting Startup Negotiation Engine") || cmd.contains("State 7 - Executing Heuristic Health Checks")) {
                depWaits.add(cmd);
            } else {
                depStarts.add(cmd);
            }
        }

        // Add Pull Dependency Steps
        for (String pullCmd : depPulls) {
            String depName = extractImageName(pullCmd);
            steps.add(new DeploymentStep("docker pull " + depName, List.of(pullCmd), 600));
        }

        // STEP: Pull primary/app image
        steps.add(new DeploymentStep("docker pull " + serviceId, List.of("docker pull " + image), 600));

        // STEP: Create Network
        steps.add(new DeploymentStep("create network", List.of("docker network create --subnet=172.28.0.0/16 autopilot 2>/dev/null || true"), 30));

        // Start Dependency Steps
        if (!depStarts.isEmpty()) {
            java.util.Map<String, List<String>> groups = new java.util.LinkedHashMap<>();
            for (String startCmd : depStarts) {
                String sName = extractServiceName(startCmd);
                groups.computeIfAbsent(sName, k -> new java.util.ArrayList<>()).add(startCmd);
            }
            for (var entry : groups.entrySet()) {
                steps.add(new DeploymentStep("start " + entry.getKey(), entry.getValue(), 120));
            }
        }

        // Wait Dependency Steps
        if (!depWaits.isEmpty()) {
            for (String waitCmd : depWaits) {
                String sName = extractServiceName(waitCmd);

                // V5.8 FIX: Never default to 3306. Resolve port from dependency type explicitly.
                // Unknown dependencies get TCP-only probe with no port assumption.
                int cp = -1;
                int hp = -1;
                String depProtocol = "TCP";
                String sNameLower = sName.toLowerCase();

                if (sNameLower.contains("mysql") || sNameLower.contains("mariadb")) {
                    cp = 3306; hp = 3306;
                    depProtocol = "DB_PING";
                } else if (sNameLower.contains("postgres") || sNameLower.contains("postgresql")) {
                    cp = 5432; hp = 5432;
                    depProtocol = "DB_PING";
                } else if (sNameLower.contains("redis") || sNameLower.contains("memcached")) {
                    cp = sNameLower.contains("memcached") ? 11211 : 6379;
                    hp = cp;
                } else if (sNameLower.contains("mongo")) {
                    cp = 27017; hp = 27017;
                    depProtocol = "DB_PING";
                } else if (sNameLower.contains("rabbit") || sNameLower.contains("amqp")) {
                    cp = 5672; hp = 5672;
                } else if (sNameLower.contains("kafka")) {
                    cp = 9092; hp = 9092;
                } else if (sNameLower.contains("elasticsearch") || sNameLower.contains("opensearch")) {
                    cp = 9200; hp = 9200;
                } else if (sNameLower.contains("minio")) {
                    cp = 9000; hp = 9000;
                } else {
                    // Unknown dependency — try to extract port from the wait command itself
                    java.util.regex.Matcher portMatcher = java.util.regex.Pattern.compile("(?::|port\\s*[:=]?\\s*)(\\d{2,5})").matcher(waitCmd);
                    if (portMatcher.find()) {
                        try {
                            cp = Integer.parseInt(portMatcher.group(1));
                            hp = cp;
                        } catch (NumberFormatException ignored) {}
                    }
                    if (cp <= 0) {
                        // Final fallback: skip this wait step entirely, log a warning
                        progressLog.accept("⚠️ Unknown dependency '" + sName + "' — cannot determine port, skipping wait step");
                        continue;
                    }
                }

                String extContainerName = extractContainerName(waitCmd);
                if (extContainerName == null) {
                    if (descriptor != null && descriptor.databaseContainerName() != null && sNameLower.contains(descriptor.databaseContainerName().replace("autopilot-", ""))) {
                        extContainerName = descriptor.databaseContainerName();
                    } else {
                        extContainerName = "autopilot-" + sName;
                        if (sNameLower.contains("redis")) {
                            extContainerName = "autopilot-redis";
                        }
                    }
                }

                DeploymentStep waitStep = new DeploymentStep("wait " + sName, List.of(waitCmd), startupTimeout + 30);
                waitStep.isWaitStep = true;
                waitStep.containerName = extContainerName;
                waitStep.containerPort = cp;
                waitStep.hostPort = hp;
                
                if ("DB_PING".equalsIgnoreCase(depProtocol)) {
                    if (sNameLower.contains("mysql") || sNameLower.contains("mariadb")) {
                        waitStep.healthPath = "docker exec " + extContainerName + " sh -c 'mysqladmin ping -uroot -p$MYSQL_ROOT_PASSWORD 2>/dev/null'";
                    } else if (sNameLower.contains("postgres") || sNameLower.contains("postgresql")) {
                        waitStep.healthPath = "docker exec " + extContainerName + " sh -c 'pg_isready -U autopilot 2>/dev/null'";
                    } else if (sNameLower.contains("mongo")) {
                        waitStep.healthPath = "docker exec " + extContainerName + " sh -c 'mongosh --eval \"db.adminCommand({ping:1})\" --quiet 2>/dev/null || mongo --eval \"db.adminCommand({ping:1})\" --quiet 2>/dev/null'";
                    } else {
                        waitStep.healthPath = "/";
                    }
                } else {
                    waitStep.healthPath = "/";
                }
                
                waitStep.protocol = depProtocol;
                waitStep.expectedStatusCodes = List.of(200, 204, 301, 302, 404);
                waitStep.framework = sName;
                waitStep.startupTimeout = startupTimeout;
                steps.add(waitStep);
            }

            // Database Existence Verification Step (V5.8)
            List<String> verificationCmds = new java.util.ArrayList<>();
            String targetDbName = null;
            for (String flag : envFlags) {
                if (flag.contains("MYSQL_DATABASE=") || flag.contains("POSTGRES_DB=") || flag.contains("MONGO_INITDB_DATABASE=")) {
                    targetDbName = flag.substring(flag.indexOf("=") + 1).replace("\"", "").replace("'", "").trim();
                    break;
                }
            }
            if (targetDbName == null || targetDbName.isBlank()) {
                for (String flag : envFlags) {
                    if (flag.contains("SPRING_DATASOURCE_URL=") || flag.contains("DATABASE_URL=") || flag.contains("MONGODB_URI=")) {
                        String val = flag.substring(flag.indexOf("=") + 1).replace("\"", "").replace("'", "").trim();
                        try {
                            String clean = val;
                            if (clean.startsWith("jdbc:")) {
                                clean = clean.substring(5);
                            }
                            int doubleSlash = clean.indexOf("//");
                            if (doubleSlash != -1) {
                                clean = clean.substring(doubleSlash + 2);
                            }
                            int slash = clean.indexOf('/');
                            if (slash != -1) {
                                String path = clean.substring(slash + 1);
                                int question = path.indexOf('?');
                                if (question != -1) {
                                    path = path.substring(0, question);
                                }
                                targetDbName = path.trim();
                            }
                        } catch (Exception ignored) {}
                        if (targetDbName != null && !targetDbName.isBlank()) {
                            break;
                        }
                    }
                }
            }
            final String db = (targetDbName != null && !targetDbName.isBlank()) ? targetDbName : "autopilotdb";

            for (String waitCmd : depWaits) {
                String sName = extractServiceName(waitCmd);
                String sNameLower = sName.toLowerCase();
                String extContainerName = extractContainerName(waitCmd);
                if (extContainerName == null) {
                    if (descriptor != null && descriptor.databaseContainerName() != null && sNameLower.contains(descriptor.databaseContainerName().replace("autopilot-", ""))) {
                        extContainerName = descriptor.databaseContainerName();
                    } else {
                        extContainerName = "autopilot-" + sName;
                    }
                }

                if (sNameLower.contains("mysql") || sNameLower.contains("mariadb")) {
                    verificationCmds.add("echo '=== VERIFYING DATABASE SCHEMAS ==='");
                    verificationCmds.add("docker exec " + extContainerName + " sh -c 'mysql -uroot -p$MYSQL_ROOT_PASSWORD -e \"CREATE DATABASE IF NOT EXISTS " + db + ";\"'");
                    verificationCmds.add("docker exec " + extContainerName + " sh -c 'mysql -uroot -p$MYSQL_ROOT_PASSWORD -e \"SHOW DATABASES;\"'");
                } else if (sNameLower.contains("postgres") || sNameLower.contains("postgresql")) {
                    verificationCmds.add("echo '=== VERIFYING DATABASE SCHEMAS ==='");
                    verificationCmds.add("docker exec " + extContainerName + " sh -c 'PGPASSWORD=$POSTGRES_PASSWORD psql -U postgres -c \"CREATE DATABASE " + db + ";\" 2>/dev/null || echo \"Database exists or creation failed\"'");
                    verificationCmds.add("docker exec " + extContainerName + " sh -c 'PGPASSWORD=$POSTGRES_PASSWORD psql -U postgres -l'");
                } else if (sNameLower.contains("mongo")) {
                    verificationCmds.add("echo '=== VERIFYING DATABASE SCHEMAS ==='");
                    verificationCmds.add("docker exec " + extContainerName + " sh -c 'mongosh --eval \"use " + db + "\" || mongo --eval \"use " + db + "\"'");
                }
            }
            if (!verificationCmds.isEmpty()) {
                steps.add(new DeploymentStep("verify database schema", verificationCmds, 60));
            }
        }

        // STEP: Start primary service
        List<String> primaryStartCmds = List.of(
                "docker rm -f " + containerName + " 2>/dev/null || true",
                "docker image prune -f 2>/dev/null || true",
                dockerRunCmd.toString()
        );
        steps.add(new DeploymentStep("start " + serviceId, primaryStartCmds, 120));

        // STEP: Wait primary service
        DeploymentStep primaryWaitStep = new DeploymentStep("wait " + serviceId, List.of(verifierScript), startupTimeout + 30);
        primaryWaitStep.isWaitStep = true;
        primaryWaitStep.containerName = containerName;
        primaryWaitStep.containerPort = containerPort;
        primaryWaitStep.hostPort = hostPort;
        primaryWaitStep.healthPath = healthPath;
        primaryWaitStep.protocol = protocol;
        primaryWaitStep.expectedStatusCodes = expectedStatusCodes;
        primaryWaitStep.framework = framework;
        primaryWaitStep.startupTimeout = startupTimeout;
        steps.add(primaryWaitStep);

        // Execute Steps Sequentially
        java.util.Map<String, Long> stepDurations = new java.util.LinkedHashMap<>();
        progressLog.accept("📦 Starting step-aware deployment execution (total " + steps.size() + " steps)");

        for (DeploymentStep step : steps) {
            executeStepWithMonitoring(ssmClient, instanceId, step, progressLog, stepDurations, containerName, descriptor);
        }

        // Print DeploymentExecutionTimeline
        progressLog.accept("\n📈 --- DeploymentExecutionTimeline ---");
        for (var entry : stepDurations.entrySet()) {
            progressLog.accept("⏱️ " + entry.getKey() + ": " + entry.getValue() + "s");
        }
        progressLog.accept("--------------------------------------\n");
    }

    private static String extractImageName(String cmd) {
        if (cmd == null) return "image";
        String[] parts = cmd.split("\\s+");
        for (String part : parts) {
            if (part.contains("/")) {
                String img = part.substring(part.lastIndexOf('/') + 1);
                int colon = img.indexOf(':');
                return colon != -1 ? img.substring(0, colon) : img;
            }
        }
        return "dependency";
    }

    private static String extractServiceName(String cmd) {
        if (cmd == null) return "service";
        int idx = cmd.indexOf("autopilot-");
        if (idx != -1) {
            String sub = cmd.substring(idx + 10);
            int space = sub.indexOf(' ');
            String name = space != -1 ? sub.substring(0, space) : sub;
            name = name.replaceAll("['\"`();&|<>$;]", "").trim();
            if (name.length() > 9 && name.charAt(8) == '-') {
                return name.substring(9);
            }
            return name;
        }
        return "service";
    }

    private static String extractContainerName(String script) {
        if (script == null) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("autopilot-[a-zA-Z0-9\\-]+");
        java.util.regex.Matcher matcher = pattern.matcher(script);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private void executeStepWithMonitoring(
            SsmClient ssmClient,
            String instanceId,
            DeploymentStep step,
            Consumer<String> progressLog,
            java.util.Map<String, Long> stepDurations,
            String appContainerName,
            com.autopilot.service.deployment.runtime.dependency.RuntimeContainerDescriptor descriptor
    ) throws Exception {
        if (step.isWaitStep) {
            long startTime = System.currentTimeMillis();
            try {
                StatefulWaitEngine.executeWait(
                        ssmClient,
                        instanceId,
                        step.containerName,
                        step.containerPort,
                        step.hostPort,
                        step.healthPath,
                        step.protocol,
                        step.expectedStatusCodes,
                        step.framework,
                        step.startupTimeout,
                        progressLog,
                        descriptor
                );
            } catch (Exception e) {
                collectFailureDiagnostics(ssmClient, instanceId, step.containerName, appContainerName, progressLog, descriptor);
                throw e;
            }
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            stepDurations.put(step.name, duration);
            return;
        }

        String wrappedScript = wrapCommandScript(step.name, step.commands);

        SendCommandResponse response = ssmClient.sendCommand(
                SendCommandRequest.builder()
                        .instanceIds(instanceId)
                        .documentName("AWS-RunShellScript")
                        .parameters(Map.of("commands", List.of(wrappedScript)))
                        .timeoutSeconds(step.timeoutSeconds)
                        .build()
        );

        String commandId = response.command().commandId();
        long startTime = System.currentTimeMillis();
        long lastActivityTime = System.currentTimeMillis();
        int lastStdoutLength = 0;
        int lastStderrLength = 0;
        long stallTimeoutMs = 180_000;

        while (true) {
            GetCommandInvocationResponse invocation;
            try {
                invocation = ssmClient.getCommandInvocation(
                        GetCommandInvocationRequest.builder()
                                .commandId(commandId)
                                .instanceId(instanceId)
                                .build()
                );
            } catch (InvocationDoesNotExistException ignored) {
                Thread.sleep(POLL_INTERVAL_MS);
                continue;
            }

            CommandInvocationStatus status = invocation.status();
            String stdout = invocation.standardOutputContent() != null ? invocation.standardOutputContent() : "";
            String stderr = invocation.standardErrorContent() != null ? invocation.standardErrorContent() : "";

            boolean progressMade = false;

            if (stdout.length() > lastStdoutLength) {
                String newOutput = stdout.substring(lastStdoutLength);
                for (String line : newOutput.split("\\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        progressLog.accept(trimmed);
                    }
                }
                lastStdoutLength = stdout.length();
                progressMade = true;
            }

            if (stderr.length() > lastStderrLength) {
                String newStderr = stderr.substring(lastStderrLength);
                for (String line : newStderr.split("\\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        progressLog.accept("⚠️ " + trimmed);
                    }
                }
                lastStderrLength = stderr.length();
                progressMade = true;
            }

            if (progressMade) {
                lastActivityTime = System.currentTimeMillis();
            }

            if (status == CommandInvocationStatus.SUCCESS) {
                long duration = (System.currentTimeMillis() - startTime) / 1000;
                stepDurations.put(step.name, duration);
                return;
            }

            if (status == CommandInvocationStatus.FAILED
                    || status == CommandInvocationStatus.TIMED_OUT
                    || status == CommandInvocationStatus.CANCELLED) {

                long duration = (System.currentTimeMillis() - startTime) / 1000;
                String report = generateFailureReport(step.name, step.commands.get(step.commands.size() - 1), status.name(), stdout, stderr, duration);
                progressLog.accept(report);
                
                collectFailureDiagnostics(ssmClient, instanceId, step.containerName, appContainerName, progressLog, descriptor);
                
                throw new RuntimeException("SSM Step '" + step.name + "' failed with status: " + status + "\n" + report);
            }

            long idleTime = System.currentTimeMillis() - lastActivityTime;
            if (idleTime > stallTimeoutMs) {
                collectFailureDiagnostics(ssmClient, instanceId, step.containerName, appContainerName, progressLog, descriptor);
                throw new RuntimeException("SSM Step '" + step.name + "' stalled. No progress for " + (idleTime / 1000) + "s.");
            }

            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    private String wrapCommandScript(String stepName, List<String> commands) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/bash\n");
        sb.append("set -euo pipefail\n");
        sb.append("trap 'echo \"STEP_FAILED\"; exit 1' ERR\n");
        sb.append("echo \"STEP_START: ").append(stepName).append("\"\n");
        sb.append("start_time=$(date +%s)\n");
        for (String cmd : commands) {
            sb.append(cmd).append("\n");
        }
        sb.append("duration=$(($(date +%s) - start_time))\n");
        sb.append("echo \"STEP_SUCCESS\"\n");
        sb.append("echo \"STEP_DURATION: ${duration}s\"\n");
        return sb.toString();
    }

    private String generateFailureReport(String stepName, String lastCommand, String exitStatus, String stdout, String stderr, long elapsedSec) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n==================================================\n");
        sb.append("❌ SSM EXECUTION FAILURE REPORT\n");
        sb.append("==================================================\n");
        sb.append("Current Step : ").append(stepName).append("\n");
        sb.append("Last Command : ").append(lastCommand).append("\n");
        sb.append("Status       : ").append(exitStatus).append("\n");
        sb.append("Elapsed Time : ").append(elapsedSec).append("s\n");
        sb.append("--------------------------------------------------\n");
        sb.append("[STDOUT]\n").append(stdout).append("\n");
        sb.append("[STDERR]\n").append(stderr).append("\n");
        sb.append("--------------------------------------------------\n");
        sb.append("Suggested Fix: Check if dependency is online or credentials are valid.\n");
        sb.append("==================================================\n");
        return sb.toString();
    }

    public String buildStartupVerifierScript(
            String serviceId,
            String framework,
            String containerName,
            int containerPort,
            int hostPort,
            String healthPath,
            String protocol,
            List<Integer> expectedStatusCodes,
            int startupTimeout,
            int retryPolicy,
            String dockerRunCmd
    ) {
        com.autopilot.analyzer.model.ServiceConfig dummy = new com.autopilot.analyzer.model.ServiceConfig();
        dummy.setFramework(framework);
        dummy.setPort(containerPort);
        com.autopilot.analyzer.model.DeploymentManifest manifest = new com.autopilot.analyzer.model.DeploymentManifest();
        manifest.setHealthCheckPath(healthPath);
        dummy.setDeploymentManifest(manifest);
        
        com.autopilot.service.deployment.validation.FrameworkStrategy strategy = strategyResolver.resolve(dummy);
        
        List<String> logReadinessMarkers = strategy.logReadinessMarkers();
        List<String> logCrashMarkers = strategy.logCrashMarkers();
        List<String> healthEndpoints = strategy.healthEndpoints();
        List<String> criticalEnvVars = strategy.criticalEnvVars();
        int negotiatedTimeout = strategy.startupTimeout();
        
        boolean isJvm = framework != null && (
                framework.toLowerCase().contains("spring") ||
                framework.toLowerCase().contains("quarkus") ||
                framework.toLowerCase().contains("micronaut") ||
                framework.toLowerCase().contains("java") ||
                framework.toLowerCase().contains("kotlin") ||
                framework.toLowerCase().contains("jvm")
        );

        String expectedCodesStr = expectedStatusCodes.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));

        String escapedDockerRunCmd = dockerRunCmd.replace("'", "'\"'\"'");

        String formattedHealthPath = healthPath;
        if (formattedHealthPath == null || formattedHealthPath.isBlank()) {
            formattedHealthPath = "/";
        }
        if (!formattedHealthPath.startsWith("/")) {
            formattedHealthPath = "/" + formattedHealthPath;
        }
        
        String readinessMarkersStr = logReadinessMarkers.stream()
                .map(s -> s.replace(";", "\\;"))
                .collect(Collectors.joining(";"));

        String crashMarkersStr = logCrashMarkers.stream()
                .map(s -> s.replace(";", "\\;"))
                .collect(Collectors.joining(";"));

        String healthEndpointsStr = healthEndpoints.stream()
                .map(s -> s.replace(";", "\\;"))
                .collect(Collectors.joining(";"));

        String criticalEnvsStr = criticalEnvVars.stream()
                .map(s -> s.replace(";", "\\;"))
                .collect(Collectors.joining(";"));

        String state5Script = "";
        if (isJvm) {
            state5Script =
                "echo 'DEBUG: State 5 - Monitoring Container Logs for startup status...'; " +
                "log_start_time=$(date +%s); " +
                "last_log_line_count=0; " +
                "log_progress_stagnant_count=0; " +
                "while true; do " +
                "  is_running=$(docker inspect -f '{{.State.Running}}' \"" + containerName + "\" 2>/dev/null); " +
                "  if [ \"$is_running\" != 'true' ]; then " +
                "    echo '❌ ERROR: Container exited during startup!'; " +
                "    diagnose_and_exit; " +
                "  fi; " +
                "  current_logs=$(docker logs \"" + containerName + "\" 2>&1); " +
                "  IFS=';'; " +
                "  for crash_marker in $CRASH_MARKERS; do " +
                "    if [ -n \"$crash_marker\" ] && echo \"$current_logs\" | grep -F -q \"$crash_marker\"; then " +
                "      echo \"❌ CRITICAL: Fatal startup crash marker detected: '$crash_marker'!\"; " +
                "      unset IFS; " +
                "      diagnose_and_exit; " +
                "    fi; " +
                "  done; " +
                "  unset IFS; " +
                "  found_ready='false'; " +
                "  IFS=';'; " +
                "  for ready_marker in $READINESS_MARKERS; do " +
                "    if [ -n \"$ready_marker\" ] && echo \"$current_logs\" | grep -F -q \"$ready_marker\"; then " +
                "      echo \"✅ Found readiness marker in logs: '$ready_marker'\"; " +
                "      found_ready='true'; " +
                "      unset IFS; " +
                "      break; " +
                "    fi; " +
                "  done; " +
                "  unset IFS; " +
                "  if [ \"$found_ready\" = 'true' ]; then " +
                "    break; " +
                "  fi; " +
                "  current_line_count=$(echo \"$current_logs\" | wc -l); " +
                "  if [ \"$current_line_count\" -gt \"$last_log_line_count\" ]; then " +
                "    echo \"⏳ Logs are progressing (lines: $current_line_count)...\"; " +
                "    last_log_line_count=$current_line_count; " +
                "    log_progress_stagnant_count=0; " +
                "  else " +
                "    log_progress_stagnant_count=$((log_progress_stagnant_count + 1)); " +
                "  fi; " +
                "  current_time=$(date +%s); " +
                "  elapsed=$((current_time - log_start_time)); " +
                "  if [ $elapsed -ge 60 ]; then " +
                "    if [ $log_progress_stagnant_count -ge 10 ]; then " +
                "      echo '⚠️ WARNING: Log progress has stalled. Falling back to direct port/HTTP probing...'; " +
                "      break; " +
                "    fi; " +
                "  fi; " +
                "  if [ $elapsed -ge " + negotiatedTimeout + " ]; then " +
                "    echo '❌ ERROR: Startup log monitoring timed out!'; " +
                "    diagnose_and_exit; " +
                "  fi; " +
                "  sleep 3; " +
                "done; ";
        } else {
            state5Script = "echo 'DEBUG: State 5 - Skipping Log Monitoring (Non-JVM runtime)...'; ";
        }

        return "echo '=================================================='; " +
                "echo 'DEBUG: Starting Startup Negotiation Engine'; " +
                "echo 'Service ID: " + serviceId + "'; " +
                "echo 'Framework: " + framework + "'; " +
                "echo 'Expected Container Port: " + containerPort + "'; " +
                "echo 'Expected Host Port: " + hostPort + "'; " +
                "echo 'Expected Protocol: " + protocol + "'; " +
                "echo 'Expected HTTP Codes: " + expectedCodesStr + "'; " +
                "echo 'Startup Timeout: " + negotiatedTimeout + " seconds'; " +
                "echo 'Docker Run Command: " + escapedDockerRunCmd + "'; " +
                "echo '=================================================='; " +
                
                // Define the list variables in shell (Fix 3, 4, 5, 8)
                "CRITICAL_ENVS=\"" + criticalEnvsStr + "\"; " +
                "CRASH_MARKERS=\"" + crashMarkersStr + "\"; " +
                "READINESS_MARKERS=\"" + readinessMarkersStr + "\"; " +
                "HEALTH_ENDPOINTS=\"" + healthEndpointsStr + "\"; " +
                
                // Helper function for crash diagnostics (Fix 4, 9)
                "diagnose_and_exit() { " +
                "  echo '❌ STARTUP INTEGRITY VERIFICATION FAILED'; " +
                "  echo '=================================================='; " +
                "  echo '--- STARTUP CRASH REPORT ---'; " +
                "  echo 'Service ID: " + serviceId + "'; " +
                "  echo 'Framework: " + framework + "'; " +
                "  echo 'Container Port: " + containerPort + "'; " +
                "  echo 'Host Port: " + hostPort + "'; " +
                "  exit_code=$(docker inspect -f '{{.State.ExitCode}}' \"" + containerName + "\" 2>/dev/null); " +
                "  oom_killed=$(docker inspect -f '{{.State.OOMKilled}}' \"" + containerName + "\" 2>/dev/null); " +
                "  restart_count=$(docker inspect -f '{{.State.RestartCount}}' \"" + containerName + "\" 2>/dev/null); " +
                "  echo \"Exit Code: $exit_code\"; " +
                "  echo \"OOM Killed: $oom_killed\"; " +
                "  echo \"Restart Count: $restart_count\"; " +
                "  echo '--- Container Networks ---'; " +
                "  docker inspect -f '{{json .NetworkSettings.Networks}}' \"" + containerName + "\" 2>/dev/null; " +
                "  echo '--- Container Mounts ---'; " +
                "  docker inspect -f '{{json .Mounts}}' \"" + containerName + "\" 2>/dev/null; " +
                "  echo '--- Container Environment ---'; " +
                "  docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' \"" + containerName + "\" 2>/dev/null | grep -E 'DATABASE|URL|PORT|HOST|USER|PASS|JWT'; " +
                "  echo '--- Container Logs (stdout/stderr) ---'; " +
                "  docker logs --tail 200 \"" + containerName + "\" 2>&1; " +
                "  echo '--- END STARTUP CRASH REPORT ---'; " +
                "  echo '=================================================='; " +
                "  exit 1; " +
                "}; " +
                
                // STATE 1: Container Created
                "echo 'DEBUG: State 1 - Verifying Container Creation...'; " +
                "if ! docker inspect \"" + containerName + "\" >/dev/null 2>&1; then " +
                "  echo '❌ ERROR: Container does not exist.'; " +
                "  exit 1; " +
                "fi; " +
                
                // STATE 2: Container Running
                "echo 'DEBUG: State 2 - Waiting for Container to start...'; " +
                "for i in $(seq 1 15); do " +
                "  is_running=$(docker inspect -f '{{.State.Running}}' \"" + containerName + "\" 2>/dev/null); " +
                "  if [ \"$is_running\" = 'true' ]; then " +
                "    echo '✅ Container is running.'; " +
                "    break; " +
                "  fi; " +
                "  if [ $i -eq 15 ]; then " +
                "    echo '❌ ERROR: Container failed to start.'; " +
                "    diagnose_and_exit; " +
                "  fi; " +
                "  sleep 1; " +
                "done; " +
                
                // STATE 3: PID Running
                "echo 'DEBUG: State 3 - Waiting for PID...'; " +
                "for i in $(seq 1 10); do " +
                "  pid=$(docker inspect -f '{{.State.Pid}}' \"" + containerName + "\" 2>/dev/null); " +
                "  if [ -n \"$pid\" ] && [ \"$pid\" -gt 0 ] 2>/dev/null; then " +
                "    echo \"✅ Process started with PID: $pid\"; " +
                "    break; " +
                "  fi; " +
                "  sleep 1; " +
                "done; " +
                
                // STATE 4: Environment Verification (Fix 8)
                "echo 'DEBUG: State 4 - Verifying Environment Variables...'; " +
                "container_env=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' \"" + containerName + "\" 2>/dev/null); " +
                "IFS=';'; " +
                "for env_var in $CRITICAL_ENVS; do " +
                "  if [ -n \"$env_var\" ]; then " +
                "    if echo \"$container_env\" | grep -q \"^$env_var=\"; then " +
                "      val=$(echo \"$container_env\" | grep \"^$env_var=\" | cut -d= -f2-); " +
                "      echo \"✅ Critical env var $env_var is set: $val\"; " +
                "    else " +
                "      echo \"⚠️ WARNING: Critical env var $env_var is NOT set in container!\"; " +
                "    fi; " +
                "  fi; " +
                "done; " +
                "unset IFS; " +
                
                state5Script +
                
                // STATE 6: Listening Socket (Fix 2)
                "echo 'DEBUG: State 6 - Verifying Listening Socket...'; " +
                "port_hex=$(printf '%04X' " + containerPort + "); " +
                "port_bound='false'; " +
                "for i in $(seq 1 10); do " +
                "  proc_tcp=$(docker exec \"" + containerName + "\" cat /proc/net/tcp /proc/net/tcp6 2>/dev/null); " +
                "  if echo \"$proc_tcp\" | grep -i \":$port_hex \" >/dev/null 2>&1; then " +
                "    echo '✅ Port " + containerPort + " is bound inside container (proc/net/tcp).'; " +
                "    port_bound='true'; " +
                "    break; " +
                "  fi; " +
                "  docker_port=$(docker port \"" + containerName + "\" 2>/dev/null); " +
                "  if echo \"$docker_port\" | grep -q \"" + hostPort + "\"; then " +
                "    echo '✅ Port mapping for " + hostPort + " verified on host.'; " +
                "    port_bound='true'; " +
                "    break; " +
                "  fi; " +
                "  sleep 2; " +
                "done; " +
                "if [ \"$port_bound\" = 'false' ]; then " +
                "  echo '⚠️ WARNING: Could not verify port binding. Proceeding to health probe fallback...'; " +
                "fi; " +
                
                // STATE 7: Heuristic Health Checks & HTTP Probing (Fix 5, 10)
                "echo 'DEBUG: State 7 - Executing Heuristic Health Checks...'; " +
                "http_success='false'; " +
                "probe_start_time=$(date +%s); " +
                "while true; do " +
                "  health_status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \"" + containerName + "\" 2>/dev/null); " +
                "  if [ \"$health_status\" = 'healthy' ]; then " +
                "    echo '✅ Docker HEALTHCHECK reports container is HEALTHY!'; " +
                "    http_success='true'; " +
                "    break; " +
                "  fi; " +
                "  IFS=';'; " +
                "  for endpoint in $HEALTH_ENDPOINTS; do " +
                "    if [ -n \"$endpoint\" ]; then " +
                "      url=\"http://127.0.0.1:" + hostPort + "$endpoint\"; " +
                "      echo \"Probing endpoint: $url\"; " +
                "      tmp_body=$(mktemp); " +
                "      tmp_stderr=$(mktemp); " +
                "      http_code=$(curl -s -S -o \"$tmp_body\" -w \"%{http_code}\" --max-time 4 \"$url\" 2>\"$tmp_stderr\"); " +
                "      curl_exit=$?; " +
                "      if [ $curl_exit -eq 0 ]; then " +
                "        if echo \"" + expectedCodesStr + "\" | grep -q \"$http_code\"; then " +
                "          echo \"✅ HTTP status $http_code matches expected codes!\"; " +
                "          echo \"Response Body: $(cat $tmp_body | head -c 200)\"; " +
                "          http_success='true'; " +
                "          rm -f \"$tmp_body\" \"$tmp_stderr\"; " +
                "          unset IFS; " +
                "          break 2; " +
                "        else " +
                "          echo \"⚠️ HTTP status $http_code received, but not in expected codes: " + expectedCodesStr + "\"; " +
                "        fi; " +
                "      else " +
                "        echo \"⚠️ Probe failed: curl exit code $curl_exit\"; " +
                "        cat \"$tmp_stderr\"; " +
                "      fi; " +
                "      rm -f \"$tmp_body\" \"$tmp_stderr\"; " +
                "    fi; " +
                "  done; " +
                "  unset IFS; " +
                "  if nc -z -w 2 127.0.0.1 " + hostPort + " 2>/dev/null || timeout 2 bash -c \"cat < /dev/null > /dev/tcp/127.0.0.1/" + hostPort + "\" 2>/dev/null; then " +
                "    echo '✅ Host TCP port " + hostPort + " is open and accepting connections.'; " +
                "    http_success='true'; " +
                "    break; " +
                "  fi; " +
                "  curr_time=$(date +%s); " +
                "  elapsed=$((curr_time - probe_start_time)); " +
                "  if [ $elapsed -ge " + negotiatedTimeout + " ]; then " +
                "    echo '❌ ERROR: Health probing timed out!'; " +
                "    break; " +
                "  fi; " +
                "  is_running=$(docker inspect -f '{{.State.Running}}' \"" + containerName + "\" 2>/dev/null); " +
                "  if [ \"$is_running\" != 'true' ]; then " +
                "    echo '❌ ERROR: Container exited during probing!'; " +
                "    break; " +
                "  fi; " +
                "  sleep 3; " +
                "done; " +
                "if [ \"$http_success\" != 'true' ]; then " +
                "  diagnose_and_exit; " +
                "fi; " +
                
                // STATE 8: Cool-off check (Fix 10)
                "echo 'DEBUG: State 8 - Cool-off Period: Verifying application remains stable...'; " +
                "sleep 10; " +
                "is_running=$(docker inspect -f '{{.State.Running}}' \"" + containerName + "\" 2>/dev/null); " +
                "if [ \"$is_running\" != 'true' ]; then " +
                "  echo '❌ ERROR: Application crashed during stability window!'; " +
                "  diagnose_and_exit; " +
                "fi; " +
                "echo '✅ SUCCESS: Startup Negotiation completed. Application is STABLE and READY!'; ";
    }


    // =========================
    // UPDATE NGINX
    // =========================
    public void updateNginx(
            String instanceId,
            String nginxConfig,
            String region,
            AwsCredentialsDto creds
    ) throws Exception {

        SsmClient ssmClient = buildSsmClient(creds, region);

        // Encode entire config as base64 — prevents SSM from mangling newlines
        // and avoids any shell escaping issues with special characters in the config
        String b64Config = Base64.getEncoder().encodeToString(nginxConfig.getBytes());

        List<String> commands = List.of(

                // FIX 1: Split nginx install into separate commands — do NOT put
                // apt-get update && apt-get install on the same line as 'which nginx || ...'
                // because: (which nginx) succeeds → || short-circuits → but '&&' then runs
                // apt-get update anyway. Each command is its own list entry.
                "which nginx || apt-get update -qq",
                "which nginx || apt-get install -y nginx",

                // Ensure conf.d exists
                "mkdir -p /etc/nginx/conf.d",

                // Clean ALL nginx config locations to avoid any conflicts
                "rm -f /etc/nginx/sites-enabled/default",
                "rm -f /etc/nginx/conf.d/*.conf",

                // Write config safely — base64 decode, no heredoc, no newline mangling
                "echo '" + b64Config + "' | base64 -d > /etc/nginx/conf.d/autopilot.conf",

                // Print config to SSM stdout for easy debugging in CloudWatch / logs
                "echo '===== NGINX CONFIG ====='",
                "cat /etc/nginx/conf.d/autopilot.conf",
                "echo '========================'",

                // Validate — if nginx -t fails, SSM marks command FAILED with full output
                "nginx -t || { echo 'NGINX CONFIG INVALID (see above)'; exit 1; }",

                // reload is zero-downtime; fallback to restart if reload fails
                "systemctl reload nginx || systemctl restart nginx",

                "echo 'Nginx updated OK'"
        );

        SendCommandResponse response = ssmClient.sendCommand(
                SendCommandRequest.builder()
                        .instanceIds(instanceId)
                        .documentName("AWS-RunShellScript")
                        .parameters(Map.of("commands", commands))
                        .timeoutSeconds(CMD_TIMEOUT_SECONDS)
                        .build()
        );

        waitForCommand(ssmClient, instanceId, response.command().commandId(), NOOP_LOG);
    }

    // =========================
    // GENERIC COMMAND RUNNER
    // =========================
    public void runCommand(
            String instanceId,
            String command,
            String region,
            AwsCredentialsDto creds
    ) {
        try {
            SsmClient ssmClient = buildSsmClient(creds, region);

            SendCommandResponse response = ssmClient.sendCommand(
                    SendCommandRequest.builder()
                            .instanceIds(instanceId)
                            .documentName("AWS-RunShellScript")
                            .parameters(Map.of("commands", List.of(command)))
                            .timeoutSeconds(CMD_TIMEOUT_SECONDS)
                            .build()
            );

            waitForCommand(ssmClient, instanceId, response.command().commandId(), NOOP_LOG);

        } catch (RuntimeException e) {
            throw e; // already wrapped
        } catch (Exception e) {
            throw new RuntimeException("SSM runCommand failed: " + e.getMessage(), e);
        }
    }

    public String runCommandAndGetOutput(
            String instanceId,
            String command,
            String region,
            AwsCredentialsDto creds
    ) {
        try {
            SsmClient ssmClient = buildSsmClient(creds, region);

            SendCommandResponse response = ssmClient.sendCommand(
                    SendCommandRequest.builder()
                            .instanceIds(instanceId)
                            .documentName("AWS-RunShellScript")
                            .parameters(Map.of("commands", List.of(command)))
                            .timeoutSeconds(CMD_TIMEOUT_SECONDS)
                            .build()
            );

            String commandId = response.command().commandId();
            for (int i = 0; i < POLL_ITERATIONS; i++) {
                try {
                    GetCommandInvocationResponse invocation =
                            ssmClient.getCommandInvocation(
                                    GetCommandInvocationRequest.builder()
                                            .commandId(commandId)
                                            .instanceId(instanceId)
                                            .build()
                            );

                    CommandInvocationStatus status = invocation.status();
                    if (status == CommandInvocationStatus.SUCCESS) {
                        return invocation.standardOutputContent();
                    }
                    if (status == CommandInvocationStatus.FAILED
                            || status == CommandInvocationStatus.TIMED_OUT
                            || status == CommandInvocationStatus.CANCELLED) {
                        return "ERROR: SSM command " + status + "\n" + invocation.standardOutputContent() + "\n" + invocation.standardErrorContent();
                    }
                } catch (InvocationDoesNotExistException ignored) {
                    // Command has not propagated to SSM yet - continue polling
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
            return "ERROR: SSM command timed out in Java poll loop";
        } catch (Exception e) {
            return "ERROR: Exception executing SSM command: " + e.getMessage();
        }
    }

    // =========================
    // BUILD SSM CLIENT
    // =========================
    protected SsmClient buildSsmClient(AwsCredentialsDto creds, String region) {
        if (creds == null) {
            // Fallback to local default credential provider chain (e.g. env vars or instance metadata)
            return SsmClient.builder()
                    .region(Region.of(region))
                    .build();
        }

        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                creds.getAccessKeyId(),
                creds.getSecretAccessKey(),
                creds.getSessionToken()
        );

        return SsmClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                .build();
    }

    // =========================
    // WAIT FOR SSM AGENT ONLINE
    // =========================
    private void waitForSSM(SsmClient ssmClient, String instanceId, Consumer<String> progressLog) throws Exception {

        for (int i = 0; i < 120; i++) {
            try {
                DescribeInstanceInformationResponse response =
                        ssmClient.describeInstanceInformation(
                                DescribeInstanceInformationRequest.builder()
                                        .filters(InstanceInformationStringFilter.builder()
                                                .key("InstanceIds")
                                                .values(instanceId)
                                                .build())
                                        .build()
                        );

                if (!response.instanceInformationList().isEmpty()
                        && response.instanceInformationList().get(0).pingStatus() == PingStatus.ONLINE) {
                    progressLog.accept("✅ SSM agent online after " + (i * 5) + "s");
                    return;
                }

            } catch (Exception ignored) {}

            // Log progress every 15 seconds (every 3rd iteration of 5s)
            if (i > 0 && i % 3 == 0) {
                progressLog.accept("⏳ Waiting for SSM agent... (" + (i * 5) + "s elapsed)");
            }

            Thread.sleep(5000);
        }

        throw new RuntimeException("SSM agent not ONLINE after 10 minutes on instance: " + instanceId);
    }

    // =========================
    // WAIT FOR COMMAND RESULT
    // FIX 4: poll iterations (150 × 4s = 600s) exceed CMD_TIMEOUT_SECONDS (480s)
    // so SSM always resolves to a terminal state before Java stops polling.
    // Previously 120 × 4s = 480s == CMD_TIMEOUT — a race condition where Java
    // could give up 1 poll cycle before SSM reported TIMED_OUT.
    // =========================
    private void waitForCommand(
            SsmClient ssmClient,
            String instanceId,
            String commandId,
            Consumer<String> progressLog
    ) throws Exception {

        // Track previously seen stdout/stderr to stream only new lines
        int lastStdoutLength = 0;
        int lastStderrLength = 0;

        for (int i = 0; i < POLL_ITERATIONS; i++) {
            try {
                GetCommandInvocationResponse response =
                        ssmClient.getCommandInvocation(
                                GetCommandInvocationRequest.builder()
                                        .commandId(commandId)
                                        .instanceId(instanceId)
                                        .build()
                        );

                CommandInvocationStatus status = response.status();

                // ── Stream new SSM stdout/stderr lines on EVERY poll ──
                String fullStdout = response.standardOutputContent() != null ? response.standardOutputContent() : "";
                String fullStderr = response.standardErrorContent() != null ? response.standardErrorContent() : "";

                if (fullStdout.length() > lastStdoutLength) {
                    String newOutput = fullStdout.substring(lastStdoutLength).trim();
                    if (!newOutput.isEmpty()) {
                        for (String line : newOutput.split("\\n")) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty()) {
                                progressLog.accept(trimmed);
                            }
                        }
                    }
                    lastStdoutLength = fullStdout.length();
                }

                if (fullStderr.length() > lastStderrLength) {
                    String newStderr = fullStderr.substring(lastStderrLength).trim();
                    if (!newStderr.isEmpty()) {
                        for (String line : newStderr.split("\\n")) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty()) {
                                progressLog.accept("⚠️ " + trimmed);
                            }
                        }
                    }
                    lastStderrLength = fullStderr.length();
                }

                // ── Terminal states ──
                if (status == CommandInvocationStatus.SUCCESS) {
                    return;
                }

                if (status == CommandInvocationStatus.FAILED
                        || status == CommandInvocationStatus.TIMED_OUT
                        || status == CommandInvocationStatus.CANCELLED) {

                    // Include full stdout + stderr so the error propagates all the
                    // way to deployment.setLogs() and shows up in the UI
                    String detail =
                            "\n[STDOUT]\n" + fullStdout +
                                    "\n[STDERR]\n" + fullStderr;

                    throw new RuntimeException("SSM command " + status + ":\n" + detail);
                }

                // Status is IN_PROGRESS or PENDING — keep polling
                // Log elapsed time every 30 seconds (every ~8th iteration of 4s)
                if (i > 0 && i % 8 == 0) {
                    int elapsedSec = i * POLL_INTERVAL_MS / 1000;
                    progressLog.accept("⏳ SSM command still running... (" + elapsedSec + "s elapsed)");
                }

            } catch (InvocationDoesNotExistException ignored) {
                // Command hasn't propagated to SSM yet — normal for first few polls
            }

            Thread.sleep(POLL_INTERVAL_MS);
        }

        throw new RuntimeException(
                "Java poller timed out waiting for SSM command " + commandId +
                        " on instance " + instanceId +
                        " after " + (POLL_ITERATIONS * POLL_INTERVAL_MS / 1000) + "s"
        );
    }

    public static java.util.List<String> buildDeduplicatedEnvFlags(java.util.List<String> rawFlags, java.util.Map<String, String> extraVars) {
        return buildDeduplicatedEnvFlags(rawFlags, extraVars, null);
    }

    public static java.util.List<String> buildDeduplicatedEnvFlags(java.util.List<String> rawFlags, java.util.Map<String, String> extraVars, String framework) {
        java.util.Map<String, String> merged = new java.util.LinkedHashMap<>();
        
        if (rawFlags != null) {
            for (String flag : rawFlags) {
                String trimmed = flag.trim();
                if (!trimmed.startsWith("-e ")) continue;
                String kv = trimmed.substring(3).trim();
                int eq = kv.indexOf('=');
                if (eq == -1) continue;
                String key = kv.substring(0, eq).trim();
                String val = kv.substring(eq + 1);
                if ((val.startsWith("'") && val.endsWith("'")) || (val.startsWith("\"") && val.endsWith("\""))) {
                    if (val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }
                }
                merged.put(key, val);
            }
        }
        
        if (extraVars != null) {
            for (var entry : extraVars.entrySet()) {
                String key = entry.getKey();
                boolean isDbKey = key.startsWith("SPRING_DATASOURCE_") || 
                                  key.startsWith("MYSQL_") || 
                                  key.startsWith("POSTGRES_") || 
                                  key.startsWith("MONGO_") || 
                                  key.equals("DATABASE_URL") || 
                                  key.equals("MONGODB_URI") || 
                                  key.equals("DB_HOST") || 
                                  key.equals("DB_PORT") || 
                                  key.equals("DB_NAME") || 
                                  key.equals("DB_USER") || 
                                  key.equals("DB_PASSWORD");
                if (isDbKey && merged.containsKey(key)) {
                    continue;
                }
                merged.put(key, entry.getValue());
            }
        }

        // Resolve database and cache environment variable conflicts by framework style
        if (framework != null) {
            String fw = framework.toLowerCase();
            boolean isFrontend = fw.contains("react") || fw.contains("vite") || fw.contains("angular") || 
                                 fw.contains("vue") || fw.contains("svelte") || fw.contains("static") || 
                                 fw.contains("html") || fw.contains("nginx");
            if (isFrontend) {
                // Rule: Frontend must NEVER receive database/cache variables (ADR-010 / Bug 3 & 4)
                merged.keySet().removeIf(key -> 
                    key.equalsIgnoreCase("SPRING_DATASOURCE_URL") || key.equalsIgnoreCase("SPRING_DATASOURCE_USERNAME") || 
                    key.equalsIgnoreCase("SPRING_DATASOURCE_PASSWORD") || key.equalsIgnoreCase("SPRING_DATA_MONGODB_URI") || 
                    key.equalsIgnoreCase("SPRING_DATA_REDIS_HOST") || key.equalsIgnoreCase("SPRING_DATA_REDIS_PORT") || 
                    key.equalsIgnoreCase("SPRING_REDIS_HOST") || key.equalsIgnoreCase("SPRING_DATA_REDIS_PORT") ||
                    key.equalsIgnoreCase("DATABASE_URL") || key.equalsIgnoreCase("DB_HOST") || key.equalsIgnoreCase("DB_PORT") || 
                    key.equalsIgnoreCase("DB_NAME") || key.equalsIgnoreCase("DB_USER") || key.equalsIgnoreCase("DB_PASSWORD") || 
                    key.equalsIgnoreCase("MONGO_URL") || key.equalsIgnoreCase("MONGODB_URI") || key.equalsIgnoreCase("REDIS_URL") || 
                    key.equalsIgnoreCase("REDIS_HOST") || key.equalsIgnoreCase("REDIS_PORT") || key.equalsIgnoreCase("CACHE_URL") ||
                    key.equalsIgnoreCase("DB_CONNECTION") || key.equalsIgnoreCase("DB_DATABASE") || key.equalsIgnoreCase("DB_USERNAME")
                );
            } else {
                boolean isSpring = fw.contains("spring") || fw.contains("quarkus") || fw.contains("java") || fw.contains("kotlin");
                if (isSpring) {
                    merged.keySet().removeIf(key -> 
                        key.equalsIgnoreCase("DATABASE_URL") || key.equalsIgnoreCase("DB_HOST") || key.equalsIgnoreCase("DB_PORT") || 
                        key.equalsIgnoreCase("DB_NAME") || key.equalsIgnoreCase("DB_USER") || key.equalsIgnoreCase("DB_PASSWORD") || 
                        key.equalsIgnoreCase("MONGO_URL") || key.equalsIgnoreCase("MONGODB_URI") || key.equalsIgnoreCase("REDIS_URL") || 
                        key.equalsIgnoreCase("REDIS_HOST") || key.equalsIgnoreCase("REDIS_PORT") || key.equalsIgnoreCase("CACHE_URL")
                    );
                } else {
                    merged.keySet().removeIf(key -> 
                        key.equalsIgnoreCase("SPRING_DATASOURCE_URL") || key.equalsIgnoreCase("SPRING_DATASOURCE_USERNAME") || 
                        key.equalsIgnoreCase("SPRING_DATASOURCE_PASSWORD") || key.equalsIgnoreCase("SPRING_DATA_MONGODB_URI") || 
                        key.equalsIgnoreCase("SPRING_DATA_REDIS_HOST") || key.equalsIgnoreCase("SPRING_DATA_REDIS_PORT") || 
                        key.equalsIgnoreCase("SPRING_REDIS_HOST") || key.equalsIgnoreCase("SPRING_DATA_REDIS_PORT")
                    );
                }
            }
        }
        
        java.util.List<String> result = new java.util.ArrayList<>();
        for (var entry : merged.entrySet()) {
            String value = entry.getValue();
            String escaped = value.replace("'", "'\\''");
            result.add("-e " + entry.getKey() + "='" + escaped + "'");
        }
        return result;
    }

    public static void checkForDuplicateEnvKeys(java.util.List<String> flags) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        java.util.List<String> duplicates = new java.util.ArrayList<>();
        for (String flag : flags) {
            String trimmed = flag.trim();
            if (!trimmed.startsWith("-e ")) continue;
            String kv = trimmed.substring(3).trim();
            int eq = kv.indexOf('=');
            if (eq == -1) continue;
            String key = kv.substring(0, eq).trim();
            if (!keys.add(key)) {
                duplicates.add(key);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Invariant Violation: Duplicate environment key(s) detected: " + duplicates);
        }
    }

    public static void validateDockerRunCommand(String cmd) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\s+-e\\s+([^\\s=]+)=");
        java.util.regex.Matcher matcher = pattern.matcher(cmd);
        java.util.Set<String> keys = new java.util.HashSet<>();
        java.util.List<String> duplicates = new java.util.ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!keys.add(key)) {
                duplicates.add(key);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Invariant Violation: Docker run command contains duplicate environment key(s): " 
                + duplicates + " in command: " + cmd);
        }
    }

    private void collectFailureDiagnostics(
            SsmClient ssmClient,
            String instanceId,
            String dependencyContainer,
            String appContainer,
            Consumer<String> progressLog,
            com.autopilot.service.deployment.runtime.dependency.RuntimeContainerDescriptor descriptor
    ) {
        if (descriptor != null) {
            String expectedApp = descriptor.applicationContainerName();
            if (appContainer != null && !StatefulWaitEngine.isContainerNameMatching(appContainer, expectedApp, descriptor.databaseContainerName())) {
                throw new IllegalStateException("Assertion Failed: Container name mismatch during diagnostics! " +
                        "Diagnosing container: '" + appContainer + "', but descriptor expects: '" + expectedApp + "'");
            }
        }
        progressLog.accept("\n🚨 ===== DEPLOYMENT FAILURE RUNTIME DIAGNOSTICS =====");
        progressLog.accept("Collecting host container status, logs, and network configuration...");
        
        List<String> diagnosticCmds = new java.util.ArrayList<>();
        diagnosticCmds.add("echo '=== DOCKER PS ===' && docker ps -a");
        diagnosticCmds.add("echo '=== AUTOPILOT NETWORK ===' && docker network inspect autopilot 2>/dev/null || echo 'No network'");
        diagnosticCmds.add("echo '=== DOCKER VOLUME INSPECT ===' && docker volume ls && docker volume inspect $(docker volume ls -q) 2>/dev/null || true");
        
        if (dependencyContainer != null && !dependencyContainer.isBlank()) {
            diagnosticCmds.add("echo '=== DEPENDENCY INSPECT: " + dependencyContainer + " ===' && docker inspect " + dependencyContainer + " 2>/dev/null");
            diagnosticCmds.add("echo '=== DEPENDENCY LOGS (LAST 150 LINES): " + dependencyContainer + " ===' && docker logs --tail 150 " + dependencyContainer + " 2>&1");
        }
        
        if (appContainer != null && !appContainer.isBlank()) {
            diagnosticCmds.add("echo '=== APPLICATION INSPECT: " + appContainer + " ===' && docker inspect " + appContainer + " 2>/dev/null");
            diagnosticCmds.add("echo '=== APPLICATION LOGS (LAST 150 LINES): " + appContainer + " ===' && docker logs --tail 150 " + appContainer + " 2>&1");
        }

        diagnosticCmds.add("echo '=== JOURNALCTL DOCKER ===' && journalctl -u docker -n 50 2>/dev/null || true");
        diagnosticCmds.add("echo '=== JOURNALCTL amazon-ssm-agent ===' && journalctl -u amazon-ssm-agent -n 50 2>/dev/null || true");
        diagnosticCmds.add("echo '=== SYSTEMCTL STATUS DOCKER ===' && systemctl status docker 2>/dev/null || true");
        diagnosticCmds.add("echo '=== SYSTEMCTL STATUS amazon-ssm-agent ===' && systemctl status amazon-ssm-agent 2>/dev/null || true");
        diagnosticCmds.add("echo '=== FREE MEMORY ===' && free -h");
        diagnosticCmds.add("echo '=== DISK SPACE ===' && df -h");
        diagnosticCmds.add("echo '=== TOP PROCESSES ===' && top -bn1 | head -n 30");
        
        for (String cmd : diagnosticCmds) {
            try {
                String out = runSingleDiagnosticCommand(ssmClient, instanceId, cmd);
                progressLog.accept(out);
            } catch (Exception ex) {
                progressLog.accept("Failed to run diagnostic command [" + cmd + "]: " + ex.getMessage());
            }
        }
        progressLog.accept("===== END DIAGNOSTICS =====\n");
    }

    private String runSingleDiagnosticCommand(SsmClient ssmClient, String instanceId, String command) {
        try {
            SendCommandResponse response = ssmClient.sendCommand(
                    SendCommandRequest.builder()
                            .instanceIds(instanceId)
                            .documentName("AWS-RunShellScript")
                            .parameters(Map.of("commands", List.of(command)))
                            .timeoutSeconds(120)
                            .build()
            );
            String commandId = response.command().commandId();
            for (int i = 0; i < 30; i++) {
                try {
                    GetCommandInvocationResponse invocation = ssmClient.getCommandInvocation(
                            GetCommandInvocationRequest.builder()
                                    .commandId(commandId)
                                    .instanceId(instanceId)
                                    .build()
                    );
                    CommandInvocationStatus status = invocation.status();
                    if (status == CommandInvocationStatus.SUCCESS) {
                        return invocation.standardOutputContent() + "\n" + invocation.standardErrorContent();
                    }
                    if (status == CommandInvocationStatus.FAILED
                            || status == CommandInvocationStatus.TIMED_OUT
                            || status == CommandInvocationStatus.CANCELLED) {
                        return "ERROR: " + status + "\nSTDOUT:\n" + invocation.standardOutputContent() + "\nSTDERR:\n" + invocation.standardErrorContent();
                    }
                } catch (InvocationDoesNotExistException ignored) {
                }
                Thread.sleep(300);
            }
            return "ERROR: Timeout waiting for command completion";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}