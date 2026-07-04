package com.autopilot;

import com.autopilot.service.aws.RegistryUploadEngine;
import com.autopilot.service.aws.RegistryUploadReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.BatchCheckLayerAvailabilityRequest;
import software.amazon.awssdk.services.ecr.model.BatchCheckLayerAvailabilityResponse;
import software.amazon.awssdk.services.ecr.model.Layer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Deployrix V5.9 — Registry Upload Reliability Engine Regression Suite")
public class V59RegistryUploadEngineHardeningTest {

    @Test
    @DisplayName("RegistryUploadEngine detects retryable errors (connection reset, TLS timeout, EOF) and resumes on ECR layer match")
    void testRegistryUploadResiliency() throws Exception {
        EcrClient ecrClient = mock(EcrClient.class);
        
        // Mock layer availability responses. 
        // In ECR, initially 1 layer exists. During retries, more layers can be reported as completed (resumed).
        AtomicInteger ecrQueryCount = new AtomicInteger(0);
        
        when(ecrClient.batchCheckLayerAvailability(any(BatchCheckLayerAvailabilityRequest.class)))
                .thenAnswer(invocation -> {
                    int count = ecrQueryCount.incrementAndGet();
                    if (count == 1) {
                        // First query: Only the first layer exists in ECR
                        return BatchCheckLayerAvailabilityResponse.builder()
                                .layers(Layer.builder()
                                        .layerDigest("sha256:d5530b134d1b7470fdf5eb725c4efebc1d3be46702283e1c6b6534be01c34a2e")
                                        .layerSize(5000L)
                                        .build())
                                .build();
                    } else {
                        // Subsequent queries: Both layers are now in ECR (simulating partial upload / successful resume)
                        return BatchCheckLayerAvailabilityResponse.builder()
                                .layers(
                                        Layer.builder().layerDigest("sha256:d5530b134d1b7470fdf5eb725c4efebc1d3be46702283e1c6b6534be01c34a2e").layerSize(5000L).build(),
                                        Layer.builder().layerDigest("sha256:c0eff722f46be22e1b1a7747e9282361b2e1e07b22ff37bc1dfa1f81d11b22e1").layerSize(8000L).build()
                                )
                                .build();
                    }
                });

        List<String> progressLogs = new ArrayList<>();
        
        // Create an anonymous subclass of RegistryUploadEngine to stub out OS process dependencies.
        RegistryUploadEngine engine = new RegistryUploadEngine() {
            private int pushAttempt = 0;

            @Override
            protected Process startInspectProcess(String imageName) throws IOException {
                Process p = mock(Process.class);
                String inspectOutput = "[\"sha256:d5530b134d1b7470fdf5eb725c4efebc1d3be46702283e1c6b6534be01c34a2e\",\"sha256:c0eff722f46be22e1b1a7747e9282361b2e1e07b22ff37bc1dfa1f81d11b22e1\"]";
                when(p.getInputStream()).thenReturn(new ByteArrayInputStream(inspectOutput.getBytes()));
                try {
                    when(p.waitFor()).thenReturn(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return p;
            }

            @Override
            protected Process startPushProcess(String fullImageUri) throws IOException {
                pushAttempt++;
                Process p = mock(Process.class);
                String stdout;
                int exitCode;

                if (pushAttempt == 1) {
                    // Simulate connection reset
                    stdout = "c0eff722f46b: Preparing\nconnection reset by peer\n";
                    exitCode = 1;
                } else if (pushAttempt == 2) {
                    // Simulate TLS timeout
                    stdout = "c0eff722f46b: Preparing\nTLS handshake timeout\n";
                    exitCode = 1;
                } else if (pushAttempt == 3) {
                    // Simulate EOF
                    stdout = "c0eff722f46b: Preparing\nEOF\n";
                    exitCode = 1;
                } else {
                    // Success
                    stdout = "c0eff722f46b: Pushed\n";
                    exitCode = 0;
                }

                when(p.getInputStream()).thenReturn(new ByteArrayInputStream(stdout.getBytes()));
                try {
                    when(p.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
                    when(p.waitFor()).thenReturn(exitCode);
                    when(p.exitValue()).thenReturn(exitCode);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return p;
            }
        };

        // Run the upload engine
        RegistryUploadReport report = engine.uploadImage(
                ecrClient,
                "my-large-image",
                "123456789012.dkr.ecr.us-east-1.amazonaws.com/my-large-image:latest",
                progressLogs::add
        );

        System.out.println("TEST DEBUG progressLogs: " + progressLogs);
        // Verify report outcomes
        assertNotNull(report);
        assertTrue(report.isSuccess());
        assertEquals("my-large-image", report.getImageName());
        assertEquals("123456789012.dkr.ecr.us-east-1.amazonaws.com", report.getRegistry());
        assertEquals(2, report.getTotalLayers());
        
        // Assert that logs captured the retries and custom progress streaming
        assertTrue(progressLogs.stream().anyMatch(log -> log.contains("connection reset by peer")));
        assertTrue(progressLogs.stream().anyMatch(log -> log.contains("TLS handshake timeout")));
        assertTrue(progressLogs.stream().anyMatch(log -> log.contains("EOF")));
        assertTrue(progressLogs.stream().anyMatch(log -> log.contains("Retryable error detected")));
        assertTrue(progressLogs.stream().anyMatch(log -> log.contains("Querying ECR for existing layers")));
        assertTrue(progressLogs.stream().anyMatch(log -> log.contains("Push Attempt #4")));
        assertTrue(progressLogs.stream().anyMatch(log -> log.contains("Push completed successfully")));
    }
}
