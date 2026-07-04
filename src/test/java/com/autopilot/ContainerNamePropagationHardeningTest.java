package com.autopilot;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.service.deployment.RuntimeInspectorService;
import com.autopilot.service.deployment.runtime.dependency.RuntimeContainerDescriptor;
import com.autopilot.service.deployment.validation.StrategyResolver;
import com.autopilot.service.infrastructure.ec2.SSMDeployService;
import com.autopilot.service.infrastructure.ec2.StatefulWaitEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Deployrix V5.8 — Container Name Propagation & Assertion Regression Suite")
public class ContainerNamePropagationHardeningTest {

    private SsmClient mockSsmClient() {
        SsmClient ssmClient = mock(SsmClient.class);
        SendCommandResponse sendResp = SendCommandResponse.builder()
                .command(Command.builder().commandId("cmd-success").build())
                .build();
        when(ssmClient.sendCommand(any(SendCommandRequest.class))).thenReturn(sendResp);

        GetCommandInvocationResponse cmdResp = GetCommandInvocationResponse.builder()
                .status(CommandInvocationStatus.SUCCESS)
                .standardOutputContent(
                        "Event: ContainerCreated\n" +
                        "Event: ProcessStarted\n" +
                        "Event: PortBound\n" +
                        "Event: ApplicationReady\n" +
                        "Event: ApplicationStable\n" +
                        "✅ SUCCESS: Stateful Wait completed."
                )
                .build();
        when(ssmClient.getCommandInvocation(any(GetCommandInvocationRequest.class))).thenReturn(cmdResp);
        return ssmClient;
    }

    @Test
    @DisplayName("API container with -api suffix and database matches descriptor correctly")
    void testApiContainerSuffixAndDatabaseMatching() {
        RuntimeContainerDescriptor descriptor = new RuntimeContainerDescriptor(
                "autopilot-deploy123-api",
                "autopilot-mysql",
                "autopilot",
                8080,
                3306
        );

        SsmClient ssm = mockSsmClient();

        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssm,
                    "i-123",
                    "autopilot-deploy123-api",
                    8080,
                    8080,
                    "/health",
                    "HTTP",
                    List.of(200),
                    "SPRING_BOOT",
                    5,
                    System.out::println,
                    descriptor
            );
        });

        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssm,
                    "i-123",
                    "autopilot-mysql",
                    3306,
                    3306,
                    "/",
                    "TCP",
                    List.of(200),
                    "mysql",
                    5,
                    System.out::println,
                    descriptor
            );
        });
    }

    @Test
    @DisplayName("Frontend container without suffix matches descriptor correctly")
    void testFrontendContainerNoSuffixMatching() {
        RuntimeContainerDescriptor descriptor = new RuntimeContainerDescriptor(
                "autopilot-deploy123",
                "autopilot-postgres",
                "autopilot",
                80,
                5432
        );

        SsmClient ssm = mockSsmClient();

        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssm,
                    "i-123",
                    "autopilot-deploy123",
                    80,
                    80,
                    "/",
                    "HTTP",
                    List.of(200),
                    "react_vite",
                    5,
                    System.out::println,
                    descriptor
            );
        });
    }

    @Test
    @DisplayName("Custom suffixes (e.g. -web) match descriptor correctly")
    void testCustomSuffixMatching() {
        RuntimeContainerDescriptor descriptor = new RuntimeContainerDescriptor(
                "autopilot-deploy123-web",
                "autopilot-mongo",
                "autopilot",
                3000,
                27017
        );

        SsmClient ssm = mockSsmClient();

        assertDoesNotThrow(() -> {
            StatefulWaitEngine.executeWait(
                    ssm,
                    "i-123",
                    "autopilot-deploy123-web",
                    3000,
                    3000,
                    "/",
                    "HTTP",
                    List.of(200),
                    "next",
                    5,
                    System.out::println,
                    descriptor
            );
        });
    }

    @Test
    @DisplayName("WaitEngine throws immediate exception when container name mismatch occurs")
    void testWaitEngineThrowsImmediateOnMismatch() {
        RuntimeContainerDescriptor descriptor = new RuntimeContainerDescriptor(
                "autopilot-deploy123-api",
                "autopilot-mysql",
                "autopilot",
                8080,
                3306
        );

        // Mismatched container name (e.g. autopilot-deploy999 instead of autopilot-deploy123-api)
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            StatefulWaitEngine.executeWait(
                    mockSsmClient(),
                    "i-123",
                    "autopilot-deploy999", // mismatch
                    8080,
                    8080,
                    "/health",
                    "HTTP",
                    List.of(200),
                    "SPRING_BOOT",
                    5,
                    System.out::println,
                    descriptor
            );
        });

        assertTrue(ex.getMessage().contains("Assertion Failed: Container mismatch"));
        assertTrue(ex.getMessage().contains("autopilot-deploy123-api"));
    }

    @Test
    @DisplayName("RuntimeInspectorService inspect throws immediate exception when container name mismatch occurs")
    void testRuntimeInspectorThrowsImmediateOnMismatch() {
        RuntimeContainerDescriptor descriptor = new RuntimeContainerDescriptor(
                "autopilot-deploy123-api",
                "autopilot-mysql",
                "autopilot",
                8080,
                3306
        );

        SSMDeployService ssmService = mock(SSMDeployService.class);
        when(ssmService.runCommandAndGetOutput(any(), any(), any(), any())).thenReturn("");

        RuntimeInspectorService inspector = new RuntimeInspectorService(
                ssmService,
                mock(com.autopilot.service.log.DeploymentLogService.class)
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            inspector.inspect(
                    "i-123",
                    "autopilot-deploy999", // mismatch
                    8080,
                    "us-east-1",
                    mock(AwsCredentialsDto.class),
                    "SPRING_BOOT",
                    "deploy123",
                    "1.2.3.4",
                    "/",
                    descriptor
            );
        });

        assertTrue(ex.getMessage().contains("Assertion Failed: Container mismatch"));
        assertTrue(ex.getMessage().contains("autopilot-deploy123-api"));
    }

    @Test
    @DisplayName("SSMDeployService deployContainer launches successfully when descriptor is propagated")
    void testSSMDeployLaunchSuccess() {
        RuntimeContainerDescriptor descriptor = new RuntimeContainerDescriptor(
                "autopilot-deploy123-api",
                "autopilot-mysql",
                "autopilot",
                8080,
                3306
        );

        SsmClient ssmClient = mockSsmClient();
        // Mock describeInstanceInformation
        when(ssmClient.describeInstanceInformation(any(DescribeInstanceInformationRequest.class))).thenReturn(
                DescribeInstanceInformationResponse.builder()
                        .instanceInformationList(InstanceInformation.builder()
                                .pingStatus(PingStatus.ONLINE)
                                .build())
                        .build()
        );

        StrategyResolver resolver = mock(StrategyResolver.class);
        com.autopilot.service.deployment.validation.FrameworkStrategy strategy = mock(com.autopilot.service.deployment.validation.FrameworkStrategy.class);
        when(strategy.logReadinessMarkers()).thenReturn(List.of("Started"));
        when(strategy.logCrashMarkers()).thenReturn(List.of("Exception"));
        when(strategy.healthEndpoints()).thenReturn(List.of("/"));
        when(strategy.criticalEnvVars()).thenReturn(List.of());
        when(strategy.startupTimeout()).thenReturn(30);
        when(resolver.resolve(any())).thenReturn(strategy);

        SSMDeployService service = new SSMDeployService(resolver) {
            @Override
            protected SsmClient buildSsmClient(AwsCredentialsDto creds, String region) {
                return ssmClient;
            }
        };

        assertDoesNotThrow(() -> {
            service.deployContainer(
                    "i-123",
                    "registry/app:latest",
                    8080,
                    8080,
                    "us-east-1",
                    mock(AwsCredentialsDto.class),
                    "deploy123",
                    List.of(),
                    List.of(),
                    System.out::println,
                    "app",
                    "SPRING_BOOT",
                    "/health",
                    "HTTP",
                    List.of(200),
                    5,
                    1,
                    descriptor
            );
        });
    }
}
