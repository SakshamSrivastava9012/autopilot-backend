package com.autopilot.service.infrastructure.ec2;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.List;
import java.util.Map;

/**
 * RuntimeStateObserver continuously queries the container and network status of the deployed
 * app on the EC2 host via SSM.
 */
public class RuntimeStateObserver {

    private final SsmClient ssmClient;
    private final String instanceId;
    private final String containerName;
    private final int containerPort;
    private final int hostPort;
    private final String healthPath;
    private final String protocol;
    private final List<Integer> expectedStatusCodes;

    public RuntimeStateObserver(SsmClient ssmClient, String instanceId, String containerName,
                                int containerPort, int hostPort, String healthPath, String protocol,
                                List<Integer> expectedStatusCodes) {
        this.ssmClient = ssmClient;
        this.instanceId = instanceId;
        this.containerName = containerName;
        this.containerPort = containerPort;
        this.hostPort = hostPort;
        
        String path = (healthPath == null || healthPath.isBlank()) ? "/" : healthPath;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        this.healthPath = path;
        this.protocol = protocol;
        this.expectedStatusCodes = expectedStatusCodes;
    }

    public ObservedState observe() {
        ObservedState state = new ObservedState();
        state.setContainerName(containerName);

        // 1. Query docker inspect (state, restart count, oom killed, health status, pid, exit code)
        String inspectFormat = "{{.State.Running}};{{.State.Restarting}};{{.State.Dead}};{{.State.ExitCode}};{{.State.StartedAt}};{{.State.FinishedAt}};{{.State.OOMKilled}};{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}};{{.State.Pid}};{{.RestartCount}}";
        String inspectCmd = "docker inspect -f '" + inspectFormat + "' " + containerName + " 2>/dev/null";
        String inspectOut = runSingleCommand(inspectCmd);
        if (inspectOut != null && !inspectOut.startsWith("ERROR:") && inspectOut.contains(";")) {
            String[] parts = inspectOut.trim().split(";");
            if (parts.length >= 9) {
                state.setRunning("true".equalsIgnoreCase(parts[0].trim()));
                state.setRestarting("true".equalsIgnoreCase(parts[1].trim()));
                state.setDead("true".equalsIgnoreCase(parts[2].trim()));
                try {
                    state.setExitCode(Integer.parseInt(parts[3].trim()));
                } catch (NumberFormatException ignored) {}
                state.setStartedAt(parts[4].trim());
                state.setFinishedAt(parts[5].trim());
                state.setOomKilled("true".equalsIgnoreCase(parts[6].trim()));
                state.setDockerHealthStatus(parts[7].trim());
                try {
                    state.setPid(Integer.parseInt(parts[8].trim()));
                } catch (NumberFormatException ignored) {}
                if (parts.length >= 10) {
                    try {
                        state.setRestartCount(Integer.parseInt(parts[9].trim()));
                    } catch (NumberFormatException ignored) {}
                }
                state.setInspectSuccess(true);
            }
        }

        if (!state.isInspectSuccess()) {
            state.setRunning(false);
            state.setDockerHealthStatus("none");
            state.setPid(0);
        }

        // 2. Query listening ports
        String hexPort = String.format("%04X", containerPort);
        String portsCmd = "docker exec " + containerName + " cat /proc/net/tcp /proc/net/tcp6 2>/dev/null || true";
        String portsOut = runSingleCommand(portsCmd);
        boolean internalPortBound = portsOut != null && portsOut.toUpperCase().contains(":" + hexPort + " ");
        state.setInternalPortBound(internalPortBound);

        String dockerPortCmd = "docker port " + containerName + " 2>/dev/null || true";
        String dockerPortOut = runSingleCommand(dockerPortCmd);
        boolean hostPortBound = dockerPortOut != null && dockerPortOut.contains(String.valueOf(hostPort));
        state.setHostPortBound(hostPortBound);

        // 3. Query logs (last 100 lines)
        String logsCmd = "docker logs --tail 100 " + containerName + " 2>&1 || true";
        String logsOut = runSingleCommand(logsCmd);
        state.setLogs(logsOut != null ? logsOut.trim() : "");

        // 4. Query health probe
        // 4. Query health probe
        if (state.isRunning()) {
            if ("DB_PING".equalsIgnoreCase(protocol)) {
                String pingOut = runSingleCommand(healthPath);
                state.setTcpConnected(pingOut != null && (
                    pingOut.toUpperCase().contains("SUCCESS") ||
                    pingOut.toLowerCase().contains("alive") ||
                    pingOut.toLowerCase().contains("ok") ||
                    pingOut.trim().equals("1") ||
                    pingOut.trim().contains("1") ||
                    pingOut.toLowerCase().contains("accepting connections")
                ));
            } else {
                // TCP Probing (liveness check)
                String tcpCmd = "if nc -z -w 2 127.0.0.1 " + hostPort + " 2>/dev/null; then echo 'SUCCESS'; else timeout 2 bash -c 'cat < /dev/null > /dev/tcp/127.0.0.1/" + hostPort + "' 2>/dev/null && echo 'SUCCESS' || echo 'FAILED'; fi";
                String tcpOut = runSingleCommand(tcpCmd);
                state.setTcpConnected(tcpOut != null && tcpOut.contains("SUCCESS"));

                // HTTP Probing
                if ("HTTP".equalsIgnoreCase(protocol) || "HTTPS".equalsIgnoreCase(protocol)) {
                    String curlCmd = "curl -s -S -o /dev/null -w '%{http_code}' --max-time 4 'http://127.0.0.1:" + hostPort + healthPath + "' 2>/dev/null || true";
                    String curlOut = runSingleCommand(curlCmd);
                    try {
                        int code = Integer.parseInt(curlOut.trim());
                        state.setHealthHttpCode(code);
                        state.setHealthHttpSuccess(expectedStatusCodes.contains(code));
                    } catch (NumberFormatException ignored) {
                        state.setHealthHttpCode(-1);
                        state.setHealthHttpSuccess(false);
                    }
                }
            }
        }

        return state;
    }

    private String runSingleCommand(String command) {
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
                        return invocation.standardOutputContent();
                    }
                    if (status == CommandInvocationStatus.FAILED
                            || status == CommandInvocationStatus.TIMED_OUT
                            || status == CommandInvocationStatus.CANCELLED) {
                        return "ERROR: " + status + " -> " + invocation.standardOutputContent() + " / " + invocation.standardErrorContent();
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
