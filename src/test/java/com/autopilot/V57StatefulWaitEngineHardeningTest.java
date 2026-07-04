package com.autopilot;

import com.autopilot.service.infrastructure.ec2.ObservedState;
import com.autopilot.service.infrastructure.ec2.RuntimeStateObserver;
import com.autopilot.service.infrastructure.ec2.StatefulWaitEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Deployrix V5.7 — Universal Runtime Wait Engine & Stateful Readiness Negotiation Suite")
public class V57StatefulWaitEngineHardeningTest {

    @Test
    @DisplayName("RuntimeStateObserver parses inspect stdout and returns correct ObservedState")
    void testRuntimeStateObserverParsing() {
        SsmClient ssmClient = mock(SsmClient.class);
        String containerName = "autopilot-test";
        String instanceId = "i-123456789";

        // Mock SendCommand to return a dummy command ID
        SendCommandResponse sendResp = SendCommandResponse.builder()
                .command(Command.builder().commandId("cmd-111").build())
                .build();
        when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResp);

        // We will simulate responses for docker inspect, port binding, logs, and HTTP probe
        // 1. docker inspect returns running, exitcode, oom, health, pid
        GetCommandInvocationResponse inspectResp = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.SUCCESS)
                .standardOutputContent("true;false;false;0;2026-07-04T00:00:00Z;;false;healthy;1234")
                .build();
        
        // 2. cat /proc/net/tcp returns bound port 8080 (hex 1F90)
        GetCommandInvocationResponse portResp = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.SUCCESS)
                .standardOutputContent("00000000:1F90 00000000:0000 0A")
                .build();

        // 3. docker port returns mapping
        GetCommandInvocationResponse dockerPortResp = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.SUCCESS)
                .standardOutputContent("8080/tcp -> 0.0.0.0:8080")
                .build();

        // 4. docker logs returns standard startup logs
        GetCommandInvocationResponse logsResp = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.SUCCESS)
                .standardOutputContent("Application started successfully")
                .build();

        // 5. curl health check returns 200
        GetCommandInvocationResponse healthResp = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.SUCCESS)
                .standardOutputContent("200")
                .build();

        // 6. TCP connectivity check returns SUCCESS
        GetCommandInvocationResponse tcpResp = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.SUCCESS)
                .standardOutputContent("SUCCESS")
                .build();

        AtomicInteger callCount = new AtomicInteger(0);
        when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class))).thenAnswer(invocation -> {
            int current = callCount.getAndIncrement();
            if (current == 0) return inspectResp;       // docker inspect
            if (current == 1) return portResp;          // /proc/net/tcp
            if (current == 2) return dockerPortResp;    // docker port
            if (current == 3) return logsResp;          // docker logs
            if (current == 4) return tcpResp;           // TCP probe
            if (current == 5) return healthResp;        // HTTP probe
            return inspectResp;
        });

        RuntimeStateObserver observer = new RuntimeStateObserver(
                ssmClient, instanceId, containerName, 8080, 8080, "/health", "HTTP", List.of(200, 204)
        );

        ObservedState state = observer.observe();

        assertTrue(state.isInspectSuccess());
        assertTrue(state.isRunning());
        assertFalse(state.isRestarting());
        assertFalse(state.isDead());
        assertEquals(0, state.getExitCode());
        assertFalse(state.isOomKilled());
        assertEquals("healthy", state.getDockerHealthStatus());
        assertEquals(1234, state.getPid());
        assertTrue(state.isInternalPortBound());
        assertTrue(state.isHostPortBound());
        assertEquals("Application started successfully", state.getLogs());
        assertEquals(200, state.getHealthHttpCode());
        assertTrue(state.isHealthHttpSuccess());
        assertTrue(state.isTcpConnected());
    }

    @Test
    @DisplayName("StatefulWaitEngine transitions through all milestones and completes successfully")
    void testStatefulWaitEngineSuccess() {
        SsmClient ssmClient = mock(SsmClient.class);
        String containerName = "autopilot-test-success";
        String instanceId = "i-987654321";

        SendCommandResponse sendResp = SendCommandResponse.builder()
                .command(Command.builder().commandId("cmd-success").build())
                .build();
        when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResp);

        // Mock the single command invocation result to return the expected telemetry lines
        GetCommandInvocationResponse cmdResp = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.SUCCESS)
                .standardOutputContent(
                        "Event: ContainerCreated [autopilot-test-success]\n" +
                        "Event: ProcessStarted [PID: 1234]\n" +
                        "Event: PortBound [Port: 8080]\n" +
                        "Event: StartupStrategy [HTTP_PROBE]\n" +
                        "Event: ReadinessConfirmed [No log markers required — HTTP probe successful]\n" +
                        "Event: HealthConfirmed\n" +
                        "Event: ApplicationReady\n" +
                        "Event: ApplicationStable\n" +
                        "✅ SUCCESS: Stateful Wait completed."
                )
                .build();

        when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class))).thenReturn(cmdResp);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient,
                    instanceId,
                    containerName,
                    8080,
                    8080,
                    "/health",
                    "HTTP",
                    List.of(200),
                    "spring",
                    30, // timeout seconds
                    progressLogs::add
            );
        });

        // Verify we got the timeline events logged
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("ContainerCreated")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("ProcessStarted") || l.contains("Running") || l.contains("State: Running")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("PortBound") || l.contains("PIDReady")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("ReadinessConfirmed") || l.contains("PortBound")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("HealthConfirmed") || l.contains("HealthPassed")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("ApplicationStable") || l.contains("Stable")));
    }

    @Test
    @DisplayName("StatefulWaitEngine correctly classifies OOMKilled failure")
    void testStatefulWaitEngineOomKilled() {
        SsmClient ssmClient = mock(SsmClient.class);
        SendCommandResponse sendResp = SendCommandResponse.builder()
                .command(Command.builder().commandId("cmd-oom").build())
                .build();
        when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResp);

        // Simulate OOM Killed script output and command failure status
        GetCommandInvocationResponse oomResp = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.FAILED)
                .standardOutputContent(
                        "CRITICAL: Container exited with code 137 (OOMKilled=true)\n" +
                        "=== CONTAINER EXIT DIAGNOSTIC SNAPSHOT ===\n" +
                        "Container 'container-oom' exited with code 137\n" +
                        "OOMKilled=true\n" +
                        "Restart Count:   0\n" +
                        "Docker Health:   none\n" +
                        "Exit Code:       137\n" +
                        "Classification: OOMKilled"
                )
                .build();

        when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class))).thenReturn(oomResp);

        List<String> progressLogs = new ArrayList<>();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            StatefulWaitEngine.executeWait(
                    ssmClient,
                    "i-oom",
                    "container-oom",
                    8080,
                    8080,
                    "/health",
                    "HTTP",
                    List.of(200),
                    "spring",
                    5,
                    progressLogs::add
            );
        });

        assertTrue(ex.getMessage().contains("Classification: OOMKilled"));
    }
}
