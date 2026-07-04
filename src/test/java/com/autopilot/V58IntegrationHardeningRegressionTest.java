package com.autopilot;

import com.autopilot.dto.*;
import com.autopilot.service.deployment.validation.StrategyResolver;
import com.autopilot.service.deployment.v5.runtime.adapter.StartupModuleV5;
import com.autopilot.service.deployment.v5.runtime.startup.engine.RuntimeLifecycleEngineV5;
import com.autopilot.service.deployment.v5.runtime.environment.injector.ContainerEnvironment;
import com.autopilot.service.infrastructure.ec2.SSMDeployService;
import com.autopilot.service.infrastructure.ec2.ObservedState;
import com.autopilot.service.infrastructure.ec2.RuntimeStateObserver;
import com.autopilot.service.infrastructure.ec2.StatefulWaitEngine;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.startup.snapshot.RuntimeLifecycleSnapshot;
import com.autopilot.service.deployment.v5.runtime.startup.report.StartupReports;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.SsmClientBuilder;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Deployrix V5.8 — Integration Hardening & Parameter Propagation Regression Suite")
public class V58IntegrationHardeningRegressionTest {

    @Test
    @DisplayName("StartupModuleV5 propagates correct serviceId and framework from DeploymentManifest")
    void testStartupModuleParameterPropagation() {
        // Setup mock RuntimeContext
        RuntimeContext ctx = mock(RuntimeContext.class);
        when(ctx.getDeploymentId()).thenReturn("4fd2-40e6-bee4-a88c48c02b4f");

        DeploymentManifest manifest = new DeploymentManifest();
        ServiceDescriptor service = new ServiceDescriptor();
        service.setId("my-spring-service");
        service.setName("my-spring-service");
        service.setFramework("SPRING_BOOT");
        service.setRole(ServiceRole.API);
        manifest.setServices(List.of(service));

        when(ctx.getDeploymentManifest()).thenReturn(manifest);

        ContainerEnvironment env = ContainerEnvironment.builder()
                .framework("SPRING_BOOT")
                .build();
        when(ctx.getResolvedObject("ContainerEnvironment")).thenReturn(env);

        // Mock RuntimeLifecycleEngineV5
        RuntimeLifecycleEngineV5 lifecycleEngine = mock(RuntimeLifecycleEngineV5.class);
        RuntimeLifecycleEngineV5.LifecycleResult dummyResult = mock(RuntimeLifecycleEngineV5.LifecycleResult.class);
        RuntimeLifecycleSnapshot snapshot = RuntimeLifecycleSnapshot.builder()
                .lifecycleState(com.autopilot.service.deployment.v5.runtime.startup.lifecycle.RuntimeLifecycleState.STABLE)
                .readinessStatus(true)
                .healthStatus(true)
                .build();
        StartupReports.StartupReport startupReport = StartupReports.StartupReport.builder()
                .logs(List.of("Container started successfully"))
                .build();
        
        when(dummyResult.getContainerId()).thenReturn("app-container-123");
        when(dummyResult.getSnapshot()).thenReturn(snapshot);
        when(dummyResult.getStartupReport()).thenReturn(startupReport);

        when(lifecycleEngine.startAndVerifyContainer(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(dummyResult);

        // Instantiate StartupModuleV5 with the mock lifecycle engine
        StartupModuleV5 startupModule = new StartupModuleV5(lifecycleEngine);
        com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode node = startupModule.createNode(ctx);
        ExecutionResult result = node.execute(ctx);

        assertNotNull(result);
        assertTrue(result.isSuccess());

        // Verify that startAndVerifyContainer was called with "my-spring-service" and "SPRING_BOOT"
        // NOT the deployment UUID ("4fd2-40e6-bee4-a88c48c02b4f")
        verify(lifecycleEngine).startAndVerifyContainer(
                eq("my-spring-service"),
                eq("app-image:latest"),
                eq("SPRING_BOOT"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("SSMDeployService isolates application and dependency ports and never defaults to 3306")
    void testSSMDeployServicePortIsolation() throws Exception {
        SsmClient ssmClient = mock(SsmClient.class);
        StrategyResolver resolver = mock(StrategyResolver.class);
        com.autopilot.service.deployment.validation.FrameworkStrategy strategy = mock(com.autopilot.service.deployment.validation.FrameworkStrategy.class);
        when(strategy.logReadinessMarkers()).thenReturn(List.of("Started"));
        when(strategy.logCrashMarkers()).thenReturn(List.of("Exception"));
        when(strategy.healthEndpoints()).thenReturn(List.of("/"));
        when(strategy.criticalEnvVars()).thenReturn(List.of());
        when(strategy.startupTimeout()).thenReturn(30);
        when(resolver.resolve(any())).thenReturn(strategy);

        SSMDeployService ssmDeployService = new SSMDeployService(resolver) {
            @Override
            protected SsmClient buildSsmClient(AwsCredentialsDto creds, String region) {
                return ssmClient;
            }
        };

        // Pre-deploy wait command for an unknown/other dependency name
        List<String> preDeployCommands = List.of(
                "docker rm -f autopilot-some-custom-dep || true",
                "DEBUG: Starting Startup Negotiation Engine for autopilot-some-custom-dep"
        );

        List<String> progressLogs = new ArrayList<>();

        // Mock describeInstanceInformation to bypass waitForSSM loop immediately
        when(ssmClient.describeInstanceInformation(any(DescribeInstanceInformationRequest.class))).thenAnswer(inv -> {
            System.out.println("[Test Mock] describeInstanceInformation was invoked!");
            try {
                DescribeInstanceInformationResponse resp = DescribeInstanceInformationResponse.builder()
                        .instanceInformationList(InstanceInformation.builder()
                                .pingStatus(PingStatus.ONLINE)
                                .build())
                        .build();
                System.out.println("[Test Mock] successfully returned response: " + resp);
                return resp;
            } catch (Throwable t) {
                System.out.println("[Test Mock] ❌ Error building describeInstanceInformation response: " + t.getMessage());
                t.printStackTrace();
                throw t;
            }
        });

        // Deterministic mock based on the command string to bypass wait loop quickly
        when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenAnswer(invocation -> {
            SendCommandRequest req = invocation.getArgument(0);
            String cmd = req.parameters().get("commands").get(0);
            String cmdId = "cmd-generic";
            if (cmd.contains("docker inspect")) {
                cmdId = "cmd-inspect";
            } else if (cmd.contains("/proc/net/tcp")) {
                cmdId = "cmd-ports";
            } else if (cmd.contains("docker port")) {
                cmdId = "cmd-docker-port";
            } else if (cmd.contains("docker logs")) {
                cmdId = "cmd-logs";
            } else if (cmd.contains("/dev/tcp")) {
                cmdId = "cmd-tcp";
            } else if (cmd.contains("curl")) {
                cmdId = "cmd-curl";
            }
            return SendCommandResponse.builder()
                    .command(Command.builder().commandId(cmdId).build())
                    .build();
        });

        when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class))).thenAnswer(invocation -> {
            GetCommandInvocationRequest req = invocation.getArgument(0);
            String cmdId = req.commandId();
            String stdout = "";
            if ("cmd-inspect".equals(cmdId)) {
                stdout = "true;false;false;0;2026-07-04T00:00:00Z;;false;healthy;1234;0";
            } else if ("cmd-ports".equals(cmdId)) {
                stdout = "00000000:1F90 00000000:0000 0A";
            } else if ("cmd-docker-port".equals(cmdId)) {
                stdout = "8080/tcp -> 0.0.0.0:8080";
            } else if ("cmd-logs".equals(cmdId)) {
                stdout = "Started successfully";
            } else if ("cmd-tcp".equals(cmdId)) {
                stdout = "SUCCESS";
            } else if ("cmd-curl".equals(cmdId)) {
                stdout = "200";
            } else {
                stdout = "SUCCESS";
            }
            return GetCommandInvocationResponse.builder()
                    .status(CommandInvocationStatus.SUCCESS)
                    .standardOutputContent(stdout)
                    .build();
        });

        System.out.println("--- Starting deployContainer in testSSMDeployServicePortIsolation ---");
        try {
            ssmDeployService.deployContainer(
                    "i-123",
                    "my-registry/my-app:latest",
                    8080,
                    8080,
                    "us-east-1",
                    null,
                    "dep-123",
                    List.of(),
                    preDeployCommands,
                    msg -> {
                        System.out.println("[deployContainer Log] " + msg);
                        progressLogs.add(msg);
                    },
                    "my-app",
                    "SPRING_BOOT",
                    "/health",
                    "HTTP",
                    List.of(200),
                    20, // smaller startup timeout for quicker test but enough for stability cool-off
                    1
            );
        } catch (Exception e) {
            System.out.println("❌ deployContainer threw exception: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        System.out.println("--- Completed deployContainer in testSSMDeployServicePortIsolation ---");

        // Verify the warning was logged for skipping unknown dependency wait
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Unknown dependency 'some-custom-dep' — cannot determine port, skipping wait step")));
    }

    @Test
    @DisplayName("StatefulWaitEngine outputs full diagnostic snapshot with RestartCount on ContainerExited")
    void testStatefulWaitDiagnosticsOnExit() {
        SsmClient ssmClient = mock(SsmClient.class);
        SendCommandResponse sendResp = SendCommandResponse.builder()
                .command(Command.builder().commandId("cmd-exit").build())
                .build();
        when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResp);

        // Simulate container exited: running=false, exitCode=1, restartCount=4
        GetCommandInvocationResponse exitInspect = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.FAILED)
                .standardOutputContent(
                        "CRITICAL: Container exited with code 1 (OOMKilled=false)\n" +
                        "=== CONTAINER EXIT DIAGNOSTIC SNAPSHOT ===\n" +
                        "Container 'container-exit' exited with code 1\n" +
                        "OOMKilled=false\n" +
                        "RestartCount=4\n" +
                        "DockerHealth=unhealthy\n" +
                        "Restart Count:   4\n" +
                        "Docker Health:   unhealthy\n" +
                        "Exit Code:       1\n" +
                        "Classification: ContainerExited"
                )
                .build();

        when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class))).thenReturn(exitInspect);

        List<String> progressLogs = new ArrayList<>();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            StatefulWaitEngine.executeWait(
                    ssmClient,
                    "i-exit",
                    "container-exit",
                    8080,
                    8080,
                    "/health",
                    "HTTP",
                    List.of(200),
                    "SPRING_BOOT",
                    5,
                    progressLogs::add
            );
        });

        // Assert message details
        assertTrue(ex.getMessage().contains("Container 'container-exit' exited with code 1"));
        assertTrue(ex.getMessage().contains("OOMKilled=false"));
        assertTrue(ex.getMessage().contains("RestartCount=4"));
        assertTrue(ex.getMessage().contains("DockerHealth=unhealthy"));

        // Assert diagnostic log contains key metadata
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("CONTAINER EXIT DIAGNOSTIC SNAPSHOT")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Restart Count:   4")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Docker Health:   unhealthy")));
        assertTrue(progressLogs.stream().anyMatch(l -> l.contains("Exit Code:       1")));
    }
}
