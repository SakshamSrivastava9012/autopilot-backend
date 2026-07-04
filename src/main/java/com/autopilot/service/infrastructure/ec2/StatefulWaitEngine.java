package com.autopilot.service.infrastructure.ec2;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.*;
import java.util.function.Consumer;
import com.autopilot.service.deployment.runtime.dependency.RuntimeContainerDescriptor;

/**
 * Redesigned EC2-Native Stateful Wait Engine.
 * Executes a single, comprehensive check-and-wait script directly on the EC2 host via SSM,
 * eliminating individual polling call overhead and AWS API throttling risks.
 */
public class StatefulWaitEngine {

    public static void executeWait(
            SsmClient ssmClient,
            String instanceId,
            String containerName,
            int containerPort,
            int hostPort,
            String healthPath,
            String protocol,
            List<Integer> expectedStatusCodes,
            String framework,
            int startupTimeoutSeconds,
            Consumer<String> progressLog
    ) {
        executeWait(ssmClient, instanceId, containerName, containerPort, hostPort, healthPath, protocol, expectedStatusCodes, framework, startupTimeoutSeconds, progressLog, null);
    }

    public static void executeWait(
            SsmClient ssmClient,
            String instanceId,
            String containerName,
            int containerPort,
            int hostPort,
            String healthPath,
            String protocol,
            List<Integer> expectedStatusCodes,
            String framework,
            int startupTimeoutSeconds,
            Consumer<String> progressLog,
            RuntimeContainerDescriptor descriptor
    ) {
        progressLog.accept("🏁 Starting EC2-Native Stateful Wait Engine for container: " + containerName);
        progressLog.accept("Config -> Framework: " + framework + ", Port: " + hostPort + ", HealthPath: " + healthPath + ", Protocol: " + protocol);

        if (descriptor != null) {
            String expectedApp = descriptor.applicationContainerName();
            String expectedDb = descriptor.databaseContainerName();
            if (containerName != null && !containerName.contains("redis")) {
                if (!isContainerNameMatching(containerName, expectedApp, expectedDb)) {
                    throw new IllegalStateException("Assertion Failed: Container mismatch! Expected application '" + expectedApp 
                        + "' or database '" + expectedDb + "', but got: '" + containerName + "'");
                }
            }
        }

        String resolvedContainerName = containerName;
        if (descriptor != null) {
            if (containerName != null && containerName.equals(descriptor.databaseContainerName())) {
                resolvedContainerName = descriptor.databaseContainerName();
            } else if (containerName != null && containerName.equals(descriptor.applicationContainerName())) {
                resolvedContainerName = descriptor.applicationContainerName();
            } else if (containerName != null && descriptor.databaseContainerName() != null && containerName.contains(descriptor.databaseContainerName().replace("autopilot-", ""))) {
                resolvedContainerName = descriptor.databaseContainerName();
            } else if (containerName != null && isContainerNameMatching(containerName, descriptor.applicationContainerName(), descriptor.databaseContainerName())) {
                resolvedContainerName = containerName;
            }
        }

        // Build the native polling shell script
        StringBuilder script = new StringBuilder();
        script.append("CONTAINER_NAME=\"").append(resolvedContainerName).append("\"\n");
        script.append("HOST_PORT=").append(hostPort).append("\n");
        script.append("CONTAINER_PORT=").append(containerPort).append("\n");
        script.append("HEALTH_PATH=\"").append(healthPath != null ? healthPath.replace("\"", "\\\"") : "/").append("\"\n");
        script.append("PROTOCOL=\"").append(protocol != null ? protocol : "TCP").append("\"\n");
        script.append("FRAMEWORK=\"").append(framework != null ? framework : "generic").append("\"\n");
        script.append("STARTUP_TIMEOUT=").append(startupTimeoutSeconds).append("\n");

        script.append("EXPECTED_STATUSES=\"");
        if (expectedStatusCodes != null && !expectedStatusCodes.isEmpty()) {
            for (int i = 0; i < expectedStatusCodes.size(); i++) {
                if (i > 0) script.append(" ");
                script.append(expectedStatusCodes.get(i));
            }
        } else {
            script.append("200 201 202 204 301 302 303 307 308 400 401 403 404 405");
        }
        script.append("\"\n");

        script.append("\n");
        script.append("echo \"🏁 Starting EC2-Native Wait Engine for container: $CONTAINER_NAME\"\n");
        script.append("echo \"Config -> Framework: $FRAMEWORK, Port: $HOST_PORT, HealthPath: $HEALTH_PATH, Protocol: $PROTOCOL\"\n");
        script.append("\n");
        script.append("START_TIME=$(date +%s)\n");
        script.append("STABLE_START_TIME=0\n");
        script.append("LAST_RESTART_COUNT=-1\n");
        script.append("STABILITY_WINDOW=12\n");
        script.append("HTTP_5XX_COUNT=0\n");
        script.append("\n");
        script.append("echo \"Event: ContainerCreated [$CONTAINER_NAME]\"\n");
        script.append("\n");
        script.append("while true; do\n");
        script.append("    NOW=$(date +%s)\n");
        script.append("    ELAPSED=$((NOW - START_TIME))\n");
        script.append("    if [ $ELAPSED -gt $STARTUP_TIMEOUT ]; then\n");
        script.append("        echo \"ERROR: Timeout waiting for container readiness after ${ELAPSED}s\"\n");
        script.append("        INSPECT=$(docker inspect -f '{{.State.Running}};{{.State.ExitCode}};{{.State.OOMKilled}};{{.RestartCount}}' \"$CONTAINER_NAME\" 2>/dev/null)\n");
        script.append("        if [ -z \"$INSPECT\" ]; then\n");
        script.append("            echo \"Classification: ContainerNeverCreated\"\n");
        script.append("        else\n");
        script.append("            RUNNING=$(echo \"$INSPECT\" | cut -d';' -f1)\n");
        script.append("            EXIT_CODE=$(echo \"$INSPECT\" | cut -d';' -f2)\n");
        script.append("            OOM_KILLED=$(echo \"$INSPECT\" | cut -d';' -f3)\n");
        script.append("            RESTART_COUNT=$(echo \"$INSPECT\" | cut -d';' -f4)\n");
        script.append("            if [ \"$OOM_KILLED\" = \"true\" ]; then\n");
        script.append("                echo \"Classification: OOMKilled\"\n");
        script.append("            elif [ \"$RESTART_COUNT\" -gt 2 ]; then\n");
        script.append("                echo \"Classification: ContainerRepeatedlyCrashing\"\n");
        script.append("            else\n");
        script.append("                TCP_OK=0\n");
        script.append("                if command -v nc >/dev/null 2>&1; then\n");
        script.append("                    if nc -z -w 2 127.0.0.1 \"$HOST_PORT\" >/dev/null 2>&1; then TCP_OK=1; fi\n");
        script.append("                fi\n");
        script.append("                if [ $TCP_OK -eq 0 ]; then\n");
        script.append("                    echo \"Classification: PortNeverOpened\"\n");
        script.append("                elif [ \"$PROTOCOL\" = \"HTTP\" ] || [ \"$PROTOCOL\" = \"HTTPS\" ]; then\n");
        script.append("                    if [ $HTTP_5XX_COUNT -gt 2 ]; then\n");
        script.append("                        echo \"Classification: HTTP5xx\"\n");
        script.append("                    else\n");
        script.append("                        echo \"Classification: HTTPTimeout\"\n");
        script.append("                    fi\n");
        script.append("                else\n");
        script.append("                    echo \"Classification: HealthEndpointUnavailable\"\n");
        script.append("                fi\n");
        script.append("            fi\n");
        script.append("        fi\n");
        script.append("        exit 1\n");
        script.append("    fi\n");
        script.append("\n");
        script.append("    INSPECT=$(docker inspect -f '{{.State.Running}};{{.State.Restarting}};{{.State.Dead}};{{.State.ExitCode}};{{.State.StartedAt}};{{.State.FinishedAt}};{{.State.OOMKilled}};{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}};{{.State.Pid}};{{.RestartCount}}' \"$CONTAINER_NAME\" 2>/dev/null)\n");
        script.append("    if [ -z \"$INSPECT\" ]; then\n");
        script.append("        echo \"Waiting for container to be created...\"\n");
        script.append("        sleep 2\n");
        script.append("        continue\n");
        script.append("    fi\n");
        script.append("\n");
        script.append("    RUNNING=$(echo \"$INSPECT\" | cut -d';' -f1)\n");
        script.append("    RESTARTING=$(echo \"$INSPECT\" | cut -d';' -f2)\n");
        script.append("    DEAD=$(echo \"$INSPECT\" | cut -d';' -f3)\n");
        script.append("    EXIT_CODE=$(echo \"$INSPECT\" | cut -d';' -f4)\n");
        script.append("    STARTED_AT=$(echo \"$INSPECT\" | cut -d';' -f5)\n");
        script.append("    FINISHED_AT=$(echo \"$INSPECT\" | cut -d';' -f6)\n");
        script.append("    OOM_KILLED=$(echo \"$INSPECT\" | cut -d';' -f7)\n");
        script.append("    HEALTH_STATUS=$(echo \"$INSPECT\" | cut -d';' -f8)\n");
        script.append("    PID=$(echo \"$INSPECT\" | cut -d';' -f9)\n");
        script.append("    RESTART_COUNT=$(echo \"$INSPECT\" | cut -d';' -f10)\n");
        script.append("\n");
        script.append("    if [ \"$RUNNING\" = \"false\" ] && [ \"$RESTARTING\" = \"false\" ] && [ \"$PID\" = \"0\" ] && [ \"$EXIT_CODE\" != \"-1\" ]; then\n");
        script.append("        echo \"CRITICAL: Container exited with code $EXIT_CODE (OOMKilled=$OOM_KILLED)\"\n");
        script.append("        if [ \"$OOM_KILLED\" = \"true\" ]; then\n");
        script.append("            echo \"Classification: OOMKilled\"\n");
        script.append("            exit 2\n");
        script.append("        fi\n");
        script.append("        if [ \"$RESTART_COUNT\" -gt 2 ]; then\n");
        script.append("            echo \"Classification: ContainerRepeatedlyCrashing\"\n");
        script.append("            exit 2\n");
        script.append("        fi\n");
        script.append("        echo \"Container exited, but restart count is low ($RESTART_COUNT). Tolerating temporary startup restart.\"\n");
        script.append("    fi\n");
        script.append("\n");
        script.append("    if [ $LAST_RESTART_COUNT -eq -1 ]; then\n");
        script.append("        LAST_RESTART_COUNT=$RESTART_COUNT\n");
        script.append("    elif [ $RESTART_COUNT -gt $LAST_RESTART_COUNT ]; then\n");
        script.append("        echo \"Warning: Container restarted. Restart count: $RESTART_COUNT. Waiting for stabilization...\"\n");
        script.append("        LAST_RESTART_COUNT=$RESTART_COUNT\n");
        script.append("        STABLE_START_TIME=0\n");
        script.append("    fi\n");
        script.append("\n");
        script.append("    HEX_PORT=$(printf '%04X' \"$CONTAINER_PORT\")\n");
        script.append("    INTERNAL_PORT_BOUND=0\n");
        script.append("    if docker exec \"$CONTAINER_NAME\" cat /proc/net/tcp /proc/net/tcp6 2>/dev/null | grep -iq \":${HEX_PORT} \"; then\n");
        script.append("        INTERNAL_PORT_BOUND=1\n");
        script.append("    fi\n");
        script.append("    HOST_PORT_BOUND=0\n");
        script.append("    if docker port \"$CONTAINER_NAME\" 2>/dev/null | grep -q \"$HOST_PORT\"; then\n");
        script.append("        HOST_PORT_BOUND=1\n");
        script.append("    fi\n");
        script.append("    TCP_CONNECTED=0\n");
        script.append("    if command -v nc >/dev/null 2>&1; then\n");
        script.append("        if nc -z -w 2 127.0.0.1 \"$HOST_PORT\" >/dev/null 2>&1; then TCP_CONNECTED=1; fi\n");
        script.append("    else\n");
        script.append("        if timeout 2 bash -c \"cat < /dev/null > /dev/tcp/127.0.0.1/${HOST_PORT}\" 2>/dev/null; then TCP_CONNECTED=1; fi\n");
        script.append("    fi\n");
        script.append("\n");
        script.append("    LOGS=$(docker logs --tail 100 \"$CONTAINER_NAME\" 2>&1)\n");
        script.append("    DET_FRAMEWORK=\"generic\"\n");
        script.append("    if echo \"$LOGS\" | grep -iq -E \"started .* in .* seconds|Tomcat started on port|JVM running for\"; then\n");
        script.append("        DET_FRAMEWORK=\"spring_boot\"\n");
        script.append("    elif echo \"$LOGS\" | grep -iq \"Quarkus .* started in\"; then\n");
        script.append("        DET_FRAMEWORK=\"quarkus\"\n");
        script.append("    elif echo \"$LOGS\" | grep -iq \"Micronaut .* started\"; then\n");
        script.append("        DET_FRAMEWORK=\"micronaut\"\n");
        script.append("    elif echo \"$LOGS\" | grep -iq -E \"listening on port|Nest application successfully started\"; then\n");
        script.append("        DET_FRAMEWORK=\"express_nest\"\n");
        script.append("    elif echo \"$LOGS\" | grep -iq -E \"Ready in|started server on\"; then\n");
        script.append("        DET_FRAMEWORK=\"nextjs\"\n");
        script.append("    elif echo \"$LOGS\" | grep -iq -E \"Uvicorn running on|Development server is running at|Running on http\"; then\n");
        script.append("        DET_FRAMEWORK=\"fastapi_django\"\n");
        script.append("    elif echo \"$LOGS\" | grep -iq \"Rails .* application starting\"; then\n");
        script.append("        DET_FRAMEWORK=\"rails\"\n");
        script.append("    elif echo \"$LOGS\" | grep -iq \"Laravel development server started\"; then\n");
        script.append("        DET_FRAMEWORK=\"laravel\"\n");
        script.append("    elif echo \"$LOGS\" | grep -iq \"Now listening on:\"; then\n");
        script.append("        DET_FRAMEWORK=\"aspnet\"\n");
        script.append("    elif echo \"$LOGS\" | grep -iq -E \"listening on|serving HTTP on\"; then\n");
        script.append("        DET_FRAMEWORK=\"go_fiber_gin\"\n");
        script.append("    fi\n");
        script.append("\n");
        script.append("    NEG_STRATEGY=\"TCP\"\n");
        script.append("    NEG_PATH=\"/\"\n");
        script.append("    if [ \"$HEALTH_STATUS\" != \"none\" ]; then\n");
        script.append("        NEG_STRATEGY=\"DOCKER_HEALTHCHECK\"\n");
        script.append("    elif [ -n \"$HEALTH_PATH\" ] && [ \"$HEALTH_PATH\" != \"/\" ]; then\n");
        script.append("        NEG_STRATEGY=\"MANIFEST_ENDPOINT\"\n");
        script.append("        NEG_PATH=\"$HEALTH_PATH\"\n");
        script.append("    elif [ \"$DET_FRAMEWORK\" = \"spring_boot\" ]; then\n");
        script.append("        ACT_CODE=$(curl -s -S -o /dev/null -w '%{http_code}' --max-time 2 \"http://127.0.0.1:${HOST_PORT}/actuator/health\" 2>/dev/null || echo \"-1\")\n");
        script.append("        if [ \"$ACT_CODE\" = \"200\" ] || [ \"$ACT_CODE\" = \"204\" ] || [ \"$ACT_CODE\" = \"401\" ] || [ \"$ACT_CODE\" = \"403\" ] || [ \"$ACT_CODE\" = \"503\" ]; then\n");
        script.append("            NEG_STRATEGY=\"HTTP_ACTUATOR\"\n");
        script.append("            NEG_PATH=\"/actuator/health\"\n");
        script.append("        else\n");
        script.append("            NEG_STRATEGY=\"HTTP_ROOT\"\n");
        script.append("            NEG_PATH=\"/\"\n");
        script.append("        fi\n");
        script.append("    elif [ \"$PROTOCOL\" = \"HTTP\" ] || [ \"$PROTOCOL\" = \"HTTPS\" ] || [ \"$HOST_PORT\" = \"80\" ] || [ \"$HOST_PORT\" = \"443\" ] || [ \"$HOST_PORT\" = \"3000\" ] || [ \"$HOST_PORT\" = \"8080\" ]; then\n");
        script.append("        NEG_STRATEGY=\"HTTP_ROOT\"\n");
        script.append("        NEG_PATH=\"/\"\n");
        script.append("    fi\n");
        script.append("\n");
        script.append("    HTTP_OK=0\n");
        script.append("    HTTP_CODE=\"-1\"\n");
        script.append("    if echo \"$NEG_STRATEGY\" | grep -q \"HTTP\\|MANIFEST\"; then\n");
        script.append("        HTTP_CODE=$(curl -s -S -o /dev/null -w '%{http_code}' --max-time 4 \"http://127.0.0.1:${HOST_PORT}${NEG_PATH}\" 2>/dev/null || echo \"-1\")\n");
        script.append("        if echo \"$EXPECTED_STATUSES\" | grep -q -w \"$HTTP_CODE\"; then\n");
        script.append("            HTTP_OK=1\n");
        script.append("        fi\n");
        script.append("        if echo \"$HTTP_CODE\" | grep -q \"^5\"; then\n");
        script.append("            HTTP_5XX_COUNT=$((HTTP_5XX_COUNT + 1))\n");
        script.append("        fi\n");
        script.append("    fi\n");
        script.append("\n");
        script.append("    CONFIDENCE=0\n");
        script.append("    if [ \"$RUNNING\" = \"true\" ] && [ \"$PID\" -gt 0 ]; then\n");
        script.append("        CONFIDENCE=$((CONFIDENCE + 30))\n");
        script.append("    fi\n");
        script.append("    if [ $INTERNAL_PORT_BOUND -eq 1 ] || [ $HOST_PORT_BOUND -eq 1 ] || [ $TCP_CONNECTED -eq 1 ]; then\n");
        script.append("        CONFIDENCE=$((CONFIDENCE + 20))\n");
        script.append("    fi\n");
        script.append("    if echo \"$NEG_STRATEGY\" | grep -q \"HTTP\\|MANIFEST\"; then\n");
        script.append("        if [ $HTTP_OK -eq 1 ]; then\n");
        script.append("            CONFIDENCE=$((CONFIDENCE + 20))\n");
        script.append("        fi\n");
        script.append("    else\n");
        script.append("        if [ $TCP_CONNECTED -eq 1 ]; then\n");
        script.append("            CONFIDENCE=$((CONFIDENCE + 20))\n");
        script.append("        fi\n");
        script.append("    fi\n");
        script.append("    if [ \"$HEALTH_STATUS\" = \"healthy\" ] || [ \"$HEALTH_STATUS\" = \"none\" ]; then\n");
        script.append("        CONFIDENCE=$((CONFIDENCE + 20))\n");
        script.append("    fi\n");
        script.append("    CRASH_FOUND=0\n");
        script.append("    if echo \"$LOGS\" | grep -iq -E \"exception|error|crash|fail|denied|fatal\"; then\n");
        script.append("        CRASH_FOUND=1\n");
        script.append("    fi\n");
        script.append("    if [ $CRASH_FOUND -eq 0 ]; then\n");
        script.append("        CONFIDENCE=$((CONFIDENCE + 10))\n");
        script.append("    fi\n");
        script.append("\n");
        script.append("    echo \"Event: ProcessStarted [PID: $PID]\"\n");
        script.append("    echo \"Event: PortBound [Port: $CONTAINER_PORT]\"\n");
        script.append("    echo \"Event: StartupStrategy [$NEG_STRATEGY]\"\n");
        script.append("    echo \"Docker State: Running=$RUNNING, ExitCode=$EXIT_CODE, OOMKilled=$OOM_KILLED, HealthStatus=$HEALTH_STATUS, PID=$PID, RestartCount=$RESTART_COUNT\"\n");
        script.append("    echo \"Ports Bound: Internal=$INTERNAL_PORT_BOUND, Host=$HOST_PORT_BOUND, TCP Connected=$TCP_CONNECTED\"\n");
        script.append("    echo \"Health HTTP Status: $HTTP_CODE\"\n");
        script.append("    echo \"Framework Detected: $DET_FRAMEWORK\"\n");
        script.append("    echo \"Negotiated Strategy: $NEG_STRATEGY (Path: $NEG_PATH)\"\n");
        script.append("    echo \"Confidence Score: ${CONFIDENCE}%\"\n");
        script.append("\n");
        script.append("    IS_READY=0\n");
        script.append("    if [ $CONFIDENCE -ge 70 ]; then\n");
        script.append("        IS_READY=1\n");
        script.append("    fi\n");
        script.append("\n");
        script.append("    if [ $IS_READY -eq 1 ]; then\n");
        script.append("        if [ $STABLE_START_TIME -eq 0 ]; then\n");
        script.append("            STABLE_START_TIME=$(date +%s)\n");
        script.append("            echo \"Event: ReadinessConfirmed [Confidence: ${CONFIDENCE}% - Negotiated: $NEG_STRATEGY]\"\n");
        script.append("            echo \"Event: HealthConfirmed\"\n");
        script.append("            echo \"Status: Ready. Waiting for stability (${STABILITY_WINDOW}s cool-off)...\"\n");
        script.append("        else\n");
        script.append("            STABLE_ELAPSED=$(( $(date +%s) - STABLE_START_TIME ))\n");
        script.append("            if [ $STABLE_ELAPSED -ge $STABILITY_WINDOW ]; then\n");
        script.append("                echo \"Event: ApplicationReady\"\n");
        script.append("                echo \"Event: ApplicationStable\"\n");
        script.append("                echo \"✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!\"\n");
        script.append("                exit 0\n");
        script.append("            fi\n");
        script.append("        fi\n");
        script.append("    else\n");
        script.append("        STABLE_START_TIME=0\n");
        script.append("    fi\n");
        script.append("    sleep 3\n");
        script.append("done\n");

        // Send the polling loop script via a single SSM command
        try {
            SendCommandResponse response = ssmClient.sendCommand(
                    SendCommandRequest.builder()
                            .instanceIds(instanceId)
                            .documentName("AWS-RunShellScript")
                            .parameters(Map.of("commands", List.of(script.toString())))
                            .timeoutSeconds(startupTimeoutSeconds + 60)
                            .build()
            );
            String commandId = response.command().commandId();
            int lastStdoutLength = 0;

            // Stream progress logs back to the deployment dashboard
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
                    Thread.sleep(2000);
                    continue;
                }

                CommandInvocationStatus status = invocation.status();
                String stdout = invocation.standardOutputContent() != null ? invocation.standardOutputContent() : "";
                String stderr = invocation.standardErrorContent() != null ? invocation.standardErrorContent() : "";

                if (stdout.length() > lastStdoutLength) {
                    String newOutput = stdout.substring(lastStdoutLength);
                    for (String line : newOutput.split("\\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            progressLog.accept(trimmed);
                        }
                    }
                    lastStdoutLength = stdout.length();
                }

                if (status == CommandInvocationStatus.SUCCESS) {
                    progressLog.accept("✅ SUCCESS: Stateful Wait completed.");
                    return;
                }

                if (status == CommandInvocationStatus.FAILED
                        || status == CommandInvocationStatus.TIMED_OUT
                        || status == CommandInvocationStatus.CANCELLED) {
                    progressLog.accept("❌ ERROR: Wait script failed or timed out. Status: " + status);
                    if (!stderr.isEmpty()) {
                        progressLog.accept("STDERR:\n" + stderr);
                    }
                    throw new RuntimeException("SSM Wait Command failed with status: " + status + "\nStdout:\n" + stdout + "\nStderr:\n" + stderr);
                }

                Thread.sleep(3000);
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Stateful wait command execution failed: " + e.getMessage(), e);
        }
    }

    public static boolean isContainerNameMatching(String containerName, String expectedApp, String expectedDb) {
        if (containerName == null) return false;
        if (containerName.equals(expectedApp) || containerName.equals(expectedDb)) {
            return true;
        }
        if (expectedDb != null && (containerName.equals(expectedDb) || containerName.startsWith(expectedDb))) {
            return true;
        }
        if (expectedApp != null && expectedApp.startsWith("autopilot-") && containerName.startsWith("autopilot-")) {
            String rest = expectedApp.substring("autopilot-".length());
            String deployId = rest;
            if (rest.length() >= 36) {
                deployId = rest.substring(0, 36);
            }
            if (containerName.contains(deployId)) {
                return true;
            }
            String cleanExpected = expectedApp.replaceAll("-(api|web|frontend|backend)$", "");
            String cleanContainer = containerName.replaceAll("-(api|web|frontend|backend)$", "");
            if (cleanExpected.equals(cleanContainer)) {
                return true;
            }
        }
        return false;
    }
}
