package com.autopilot.service.infrastructure.ec2;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.service.aws.AwsCredentialService;
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

@Service
@RequiredArgsConstructor
public class SSMDeployService {

    private final AwsCredentialService awsCredentialService;

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
            String roleArn,
            String deploymentId
    ) throws Exception {
        deployContainer(instanceId, image, hostPort, containerPort, region, roleArn, deploymentId, List.of(), List.of(), NOOP_LOG);
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
            String roleArn,
            String deploymentId,
            List<String> envFlags,
            List<String> preDeployCommands
    ) throws Exception {
        deployContainer(instanceId, image, hostPort, containerPort, region, roleArn, deploymentId, envFlags, preDeployCommands, NOOP_LOG);
    }

    /**
     * Deploy container WITH environment variable injection, pre-deploy commands, and progress logging.
     *
     * @param envFlags          List of "-e KEY='value'" flags for docker run
     * @param preDeployCommands Commands to run before the app container (e.g., Redis provisioning)
     * @param progressLog       Callback for real-time log messages (sent to frontend)
     */
    public void deployContainer(
            String instanceId,
            String image,
            int hostPort,
            int containerPort,
            String region,
            String roleArn,
            String deploymentId,
            List<String> envFlags,
            List<String> preDeployCommands,
            Consumer<String> progressLog
    ) throws Exception {

        SsmClient ssmClient = buildSsmClient(roleArn, region);

        progressLog.accept("📡 Waiting for SSM agent to come online on " + instanceId + "...");
        waitForSSM(ssmClient, instanceId, progressLog);
        progressLog.accept("✅ SSM agent online — sending deploy commands...");

        String registry      = image.substring(0, image.indexOf('/'));
        String containerName = "autopilot-" + deploymentId;

        // Build the docker run command with env vars
        StringBuilder dockerRunCmd = new StringBuilder();
        dockerRunCmd.append(" docker run -d");
        dockerRunCmd.append(" --name ").append(containerName);
        dockerRunCmd.append(" --network autopilot");
        dockerRunCmd.append(" --restart unless-stopped");
        dockerRunCmd.append(" -p 127.0.0.1:").append(hostPort).append(":").append(containerPort);

        // Inject environment variables
        for (String flag : envFlags) {
            // Each flag looks like: -e KEY='value'
            dockerRunCmd.append(" ").append(flag);
        }

        dockerRunCmd.append(" ").append(image);
        dockerRunCmd.append(" || { echo 'docker run failed. Container logs:'; docker logs ")
                .append(containerName).append(" 2>&1 || true; exit 1; }");

        // Build the full command list
        List<String> commands = new java.util.ArrayList<>();

        // System setup
        commands.add("systemctl daemon-reload 2>/dev/null || true");
        commands.add("systemctl enable docker 2>/dev/null || true");
        commands.add("systemctl start docker 2>/dev/null || true");

        // Wait for Docker daemon
        commands.add("for i in $(seq 1 30); do" +
                " if docker info >/dev/null 2>&1; then echo \"Docker ready (attempt $i)\"; break; fi;" +
                " if [ $i -eq 30 ]; then" +
                "   echo 'ERROR: dockerd never became ready';" +
                "   journalctl -u docker --no-pager -n 50;" +
                "   exit 1;" +
                " fi;" +
                " sleep 2;" +
                " done");

        // ECR login
        commands.add("aws ecr get-login-password --region " + region +
                " | docker login --username AWS --password-stdin " + registry +
                " || { echo 'ECR login failed'; exit 1; }");

        // Pull image
        commands.add("docker pull " + image +
                " || { echo 'docker pull failed for image: " + image + "'; exit 1; }");

        // Remove old container
        commands.add("docker rm -f " + containerName + " 2>/dev/null || true");
        commands.add("docker image prune -f 2>/dev/null || true");
        
        // Create dedicated network for Autopilot containers
        commands.add("docker network create autopilot 2>/dev/null || true");

        // Run pre-deploy commands (e.g., Redis provisioning)
        commands.addAll(preDeployCommands);

        // Run the main app container with env vars injected
        commands.add(dockerRunCmd.toString());

        // Health check — wait for the container to accept connections
        commands.add("for i in $(seq 1 20); do" +
                " if curl -s --max-time 3 -o /dev/null http://127.0.0.1:" + hostPort + "; then" +
                "   echo \"Container accepting connections (attempt $i)\"; exit 0;" +
                " fi;" +
                " sleep 3;" +
                " done;" +
                " echo 'Container never accepted connections. Last 100 log lines:';" +
                " docker logs --tail 100 " + containerName + " 2>&1;" +
                " exit 1");

        progressLog.accept("📦 SSM command sent — " + commands.size() + " steps to execute on EC2");

        SendCommandResponse response = ssmClient.sendCommand(
                SendCommandRequest.builder()
                        .instanceIds(instanceId)
                        .documentName("AWS-RunShellScript")
                        .parameters(Map.of("commands", commands))
                        .timeoutSeconds(CMD_TIMEOUT_SECONDS)
                        .build()
        );

        waitForCommand(ssmClient, instanceId, response.command().commandId(), progressLog);
    }

    // =========================
    // UPDATE NGINX
    // =========================
    public void updateNginx(
            String instanceId,
            String nginxConfig,
            String region,
            String roleArn
    ) throws Exception {

        SsmClient ssmClient = buildSsmClient(roleArn, region);

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
            String roleArn
    ) {
        try {
            SsmClient ssmClient = buildSsmClient(roleArn, region);

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

    // =========================
    // BUILD SSM CLIENT
    // =========================
    private SsmClient buildSsmClient(String roleArn, String region) throws Exception {
        AwsCredentialsDto creds = awsCredentialService.assumeRole(roleArn);

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

                if (status == CommandInvocationStatus.SUCCESS) {
                    return;
                }

                if (status == CommandInvocationStatus.FAILED
                        || status == CommandInvocationStatus.TIMED_OUT
                        || status == CommandInvocationStatus.CANCELLED) {

                    // Include full stdout + stderr so the error propagates all the
                    // way to deployment.setLogs() and shows up in the UI
                    String detail =
                            "\n[STDOUT]\n" + response.standardOutputContent() +
                                    "\n[STDERR]\n" + response.standardErrorContent();

                    throw new RuntimeException("SSM command " + status + ":\n" + detail);
                }

                // Status is IN_PROGRESS or PENDING — keep polling
                // Log progress every 30 seconds (every ~8th iteration of 4s)
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
}