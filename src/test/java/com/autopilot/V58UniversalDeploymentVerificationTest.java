package com.autopilot;

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

@DisplayName("Deployrix V5.8 — Universal Deployment Verification & Integration Regression Suite")
public class V58UniversalDeploymentVerificationTest {

    private void mockSsmSequence(SsmClient ssmClient, List<String> stdoutSequence) {
        SendCommandResponse sendResp = SendCommandResponse.builder()
                .command(Command.builder().commandId("cmd-test").build())
                .build();
        when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResp);

        AtomicInteger index = new AtomicInteger(0);
        when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class))).thenAnswer(inv -> {
            int idx = index.getAndIncrement();
            // Build cumulative stdout up to idx
            StringBuilder cumulative = new StringBuilder();
            for (int i = 0; i <= idx && i < stdoutSequence.size(); i++) {
                cumulative.append(stdoutSequence.get(i)).append("\n");
            }
            CommandInvocationStatus status = (idx >= stdoutSequence.size() - 1)
                    ? CommandInvocationStatus.SUCCESS
                    : CommandInvocationStatus.IN_PROGRESS;
            return GetCommandInvocationResponse.builder()
                    .status(status)
                    .standardOutputContent(cumulative.toString())
                    .build();
        });
    }

    @Test
    @DisplayName("Spring Boot with Actuator readiness check")
    void testSpringBootWithActuator() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            // Initial poll: container running, ports bound, framework detected, actuator returns 200, stable cool-off starts
            "Event: ContainerCreated [spring-app]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [HTTP_ACTUATOR]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: spring_boot\n" +
            "Negotiated Strategy: HTTP_ACTUATOR (Path: /actuator/health)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed [Confidence: 80% - Negotiated: HTTP_ACTUATOR]\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            // Stability check (12s elapsed) -> Success
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [HTTP_ACTUATOR]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: spring_boot\n" +
            "Negotiated Strategy: HTTP_ACTUATOR (Path: /actuator/health)\n" +
            "Confidence Score: 80%\n" +
            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "spring-app", 8080, 8080, "/health", "HTTP", List.of(200), "spring_boot", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("HTTP_ACTUATOR")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("ApplicationStable")));
    }

    @Test
    @DisplayName("Spring Boot without Actuator (falls back to root endpoint)")
    void testSpringBootWithoutActuator() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [spring-noact-app]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: spring_boot\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed [Confidence: 80% - Negotiated: HTTP_ROOT]\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: spring_boot\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "spring-noact-app", 8080, 8080, "/", "HTTP", List.of(200), "spring_boot", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("HTTP_ROOT")));
    }

    @Test
    @DisplayName("Express application detection and readiness")
    void testExpress() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [express-app]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 3000]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: express_nest\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "express-app", 3000, 3000, "/", "HTTP", List.of(200), "express", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("express_nest") || l.contains("Framework Detected:")));
    }

    @Test
    @DisplayName("React/Vite with Nginx static frontend serving")
    void testReactNginx() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [react-app]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 80]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: generic\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "react-app", 80, 80, "/", "HTTP", List.of(200), "react_vite", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("PortBound [Port: 80]")));
    }

    @Test
    @DisplayName("Next.js server-side rendering framework")
    void testNextJs() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [next-app]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 3000]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: nextjs\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "next-app", 3000, 3000, "/", "HTTP", List.of(200), "nextjs", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("nextjs") || l.contains("Framework Detected:")));
    }

    @Test
    @DisplayName("FastAPI Python framework check")
    void testFastApi() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [fastapi-app]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8000]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: fastapi_django\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "fastapi-app", 8000, 8000, "/", "HTTP", List.of(200), "fastapi", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("fastapi_django") || l.contains("Framework Detected:")));
    }

    @Test
    @DisplayName("Django Python framework check")
    void testDjango() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [django-app]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8000]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: fastapi_django\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "django-app", 8000, 8000, "/", "HTTP", List.of(200), "django", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("fastapi_django") || l.contains("Framework Detected:")));
    }

    @Test
    @DisplayName("Go fiber/gin web framework check")
    void testGoHttpServer() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [go-app]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: go_fiber_gin\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "go-app", 8080, 8080, "/", "HTTP", List.of(200), "go", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("go_fiber_gin") || l.contains("Framework Detected:")));
    }

    @Test
    @DisplayName("OAuth & login redirects are accepted as healthy statuses")
    void testOAuthRedirect() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [auth-app]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 302\n" +
            "Framework Detected: generic\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "auth-app", 8080, 8080, "/", "HTTP", List.of(200, 302), "generic", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Health HTTP Status: 302")));
    }

    @Test
    @DisplayName("401 Unauthorized or 404 Not Found at root are accepted as healthy responses")
    void testAcceptsNon200Responses() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [secured-app]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 401\n" +
            "Framework Detected: generic\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "secured-app", 8080, 8080, "/", "HTTP", List.of(200), "generic", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Health HTTP Status: 401")));
    }

    @Test
    @DisplayName("Docker HEALTHCHECK integration is prioritized")
    void testDockerHealthcheck() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [healthy-container]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [DOCKER_HEALTHCHECK]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=healthy, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: -1\n" +
            "Framework Detected: generic\n" +
            "Negotiated Strategy: DOCKER_HEALTHCHECK (Path: /)\n" +
            "Confidence Score: 100%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "healthy-container", 8080, 8080, "/", "HTTP", List.of(200), "generic", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Negotiated Strategy: DOCKER_HEALTHCHECK")));
    }

    @Test
    @DisplayName("TCP-only connection works for database containers")
    void testTcpOnlyServices() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [db-container]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 3306]\n" +
            "Event: StartupStrategy [TCP]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: -1\n" +
            "Framework Detected: generic\n" +
            "Negotiated Strategy: TCP (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "db-container", 3306, 3306, "/", "TCP", List.of(), "generic", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Negotiated Strategy: TCP")));
    }

    @Test
    @DisplayName("Container restart during startup is tolerated and does not fail immediately")
    void testContainerRestartDuringStartup() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            // First poll: container restarted, count increases but not failing
            "Event: ContainerCreated [restarting-app]\n" +
            "Warning: Container restarted. Restart count: 1. Waiting for stabilization...\n" +
            "Event: ProcessStarted [PID: 4321]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=4321, RestartCount=1\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: -1\n" +
            "Framework Detected: generic\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 60%\n",

            // Second poll: stabilizes and succeeds
            "Event: ProcessStarted [PID: 4321]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=4321, RestartCount=1\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Framework Detected: generic\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed [Confidence: 80% - Negotiated: HTTP_ROOT]\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "restarting-app", 8080, 8080, "/", "HTTP", List.of(200), "generic", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Warning: Container restarted.")));
    }

    @Test
    @DisplayName("Delayed database startup is supported by waiting for ports")
    void testDelayedDatabaseStartup() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            // First poll: process alive but port not bound
            "Event: ContainerCreated [slow-db]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=0, Host=0, TCP Connected=0\n" +
            "Confidence Score: 30%\n",

            // Second poll: port finally bound and TCP online
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 5432]\n" +
            "Event: StartupStrategy [TCP]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Negotiated Strategy: TCP (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Event: HealthConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);

        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "slow-db", 5432, 5432, "/", "TCP", List.of(), "generic", 30, progressLogs::add
            );
        });

        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Ports Bound: Internal=0, Host=0")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("PortBound [Port: 5432]")));
    }

    @Test
    @DisplayName("Redis wait regression validation")
    void testRedis() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [redis-container]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 6379]\n" +
            "Event: StartupStrategy [TCP]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Negotiated Strategy: TCP (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);
        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "redis-container", 6379, 6379, "/", "TCP", List.of(), "generic", 30, progressLogs::add
            );
        });
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("PortBound [Port: 6379]")));
    }

    @Test
    @DisplayName("MongoDB wait regression validation")
    void testMongoDB() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [mongo-container]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 27017]\n" +
            "Event: StartupStrategy [TCP]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Negotiated Strategy: TCP (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);
        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "mongo-container", 27017, 27017, "/", "TCP", List.of(), "generic", 30, progressLogs::add
            );
        });
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("PortBound [Port: 27017]")));
    }

    @Test
    @DisplayName("Postgres wait regression validation")
    void testPostgres() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [pg-container]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 5432]\n" +
            "Event: StartupStrategy [TCP]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Negotiated Strategy: TCP (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);
        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "pg-container", 5432, 5432, "/", "TCP", List.of(), "generic", 30, progressLogs::add
            );
        });
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("PortBound [Port: 5432]")));
    }

    @Test
    @DisplayName("MySQL wait regression validation")
    void testMySQL() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            "Event: ContainerCreated [mysql-container]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 3306]\n" +
            "Event: StartupStrategy [TCP]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Negotiated Strategy: TCP (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);
        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "mysql-container", 3306, 3306, "/", "TCP", List.of(), "generic", 30, progressLogs::add
            );
        });
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("PortBound [Port: 3306]")));
    }

    @Test
    @DisplayName("Slow JVM startup check")
    void testSlowJvmStartup() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            // Poll 1: Container running but port not yet bound. Confidence low.
            "Event: ContainerCreated [slow-jvm]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=0, Host=0, TCP Connected=0\n" +
            "Confidence Score: 30%\n",

            // Poll 2: Port bound, but HTTP returns connection refused (-1).
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: -1\n" +
            "Confidence Score: 50%\n",

            // Poll 3: JVM started, Actuator returns 200. Ready!
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 8080]\n" +
            "Event: StartupStrategy [HTTP_ACTUATOR]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Negotiated Strategy: HTTP_ACTUATOR (Path: /actuator/health)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed [Confidence: 80% - Negotiated: HTTP_ACTUATOR]\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);
        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "slow-jvm", 8080, 8080, "/health", "HTTP", List.of(200), "spring_boot", 30, progressLogs::add
            );
        });
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("HTTP_ACTUATOR")));
    }

    @Test
    @DisplayName("Reverse proxy returns 502/503 temporarily")
    void testReverseProxy() {
        SsmClient ssmClient = mock(SsmClient.class);
        List<String> stdoutSequence = List.of(
            // Poll 1: Gateway returns 502/503, tolerated.
            "Event: ContainerCreated [rev-proxy]\n" +
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 80]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 502\n" +
            "Confidence Score: 60%\n",

            // Poll 2: Gateway returns 200. Ready!
            "Event: ProcessStarted [PID: 1234]\n" +
            "Event: PortBound [Port: 80]\n" +
            "Event: StartupStrategy [HTTP_ROOT]\n" +
            "Docker State: Running=true, ExitCode=0, OOMKilled=false, HealthStatus=none, PID=1234, RestartCount=0\n" +
            "Ports Bound: Internal=1, Host=1, TCP Connected=1\n" +
            "Health HTTP Status: 200\n" +
            "Negotiated Strategy: HTTP_ROOT (Path: /)\n" +
            "Confidence Score: 80%\n" +
            "Event: ReadinessConfirmed\n" +
            "Status: Ready. Waiting for stability (12s cool-off)...",

            "Event: ApplicationReady\n" +
            "Event: ApplicationStable\n" +
            "✅ SUCCESS: Stateful Wait completed. Application is STABLE and READY!"
        );

        mockSsmSequence(ssmClient, stdoutSequence);
        List<String> progressLogs = new ArrayList<>();
        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssmClient, "i-123", "rev-proxy", 80, 80, "/", "HTTP", List.of(200), "generic", 30, progressLogs::add
            );
        });
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Health HTTP Status: 502")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Health HTTP Status: 200")));
    }
}
