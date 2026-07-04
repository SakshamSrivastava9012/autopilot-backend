package com.autopilot;

import com.autopilot.service.BuildMetadataService;
import com.autopilot.service.ProcessLookupService;
import com.autopilot.service.VersionIntegrityValidator;
import com.autopilot.service.log.DeploymentLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class VersionIntegrityValidationTest {

    private BuildMetadataService metadataService;
    private ProcessLookupService processLookupService;
    private TestDeploymentLogService logService;
    private VersionIntegrityValidator validator;

    private static class TestDeploymentLogService extends DeploymentLogService {
        public final List<String> logs = new ArrayList<>();

        public TestDeploymentLogService() {
            super(null, null, null);
        }

        @Override
        public void info(String deploymentId, String stage, String message) {
            logs.add("INFO: " + message);
        }

        @Override
        public void warn(String deploymentId, String stage, String message) {
            logs.add("WARN: " + message);
        }

        @Override
        public void error(String deploymentId, String stage, String message) {
            logs.add("ERROR: " + message);
        }
    }

    @BeforeEach
    public void setUp() {
        metadataService = mock(BuildMetadataService.class);
        processLookupService = mock(ProcessLookupService.class);
        logService = new TestDeploymentLogService();
        validator = new VersionIntegrityValidator(metadataService, processLookupService);
    }

    /**
     * Test Case 1: When checksum matches and no duplicates exist, validation passes.
     */
    @Test
    public void testValidationSuccess() throws Exception {
        String currentPid = "12345";
        when(metadataService.getPid()).thenReturn(currentPid);
        when(metadataService.getVersion()).thenReturn("1.0.0");
        when(metadataService.getGitCommit()).thenReturn("abcdef123");
        when(metadataService.getBuildTimestamp()).thenReturn("2026-06-29T12:00:00Z");
        when(metadataService.getStartTime()).thenReturn(System.currentTimeMillis());
        when(metadataService.getRunningJarPath()).thenReturn(null); // simulation of no JAR path (IDE)

        when(processLookupService.getRunningBackendProcesses()).thenReturn(
                List.of(new ProcessLookupService.ProcessInfo(currentPid, "java -jar ..."))
        );

        assertDoesNotThrow(() -> validator.validate("test-deploy-id", logService));

        boolean foundVersion = logService.logs.stream().anyMatch(l -> l.contains("Backend Version: 1.0.0"));
        boolean foundPassed = logService.logs.stream().anyMatch(l -> l.contains("Validation Passed"));
        assertTrue(foundVersion);
        assertTrue(foundPassed);
    }

    /**
     * Test Case 2: When a JAR checksum mismatch is detected, validation throws an integrity violation.
     */
    @Test
    public void testChecksumMismatchThrowsException() throws Exception {
        String currentPid = "12345";
        when(metadataService.getPid()).thenReturn(currentPid);
        when(processLookupService.getRunningBackendProcesses()).thenReturn(
                List.of(new ProcessLookupService.ProcessInfo(currentPid, "java -jar ..."))
        );

        // Create temporary files representing running JAR and modified JAR on disk
        File tempJar = File.createTempFile("mock-backend", ".jar");
        tempJar.deleteOnExit();
        try (FileWriter fw = new FileWriter(tempJar)) {
            fw.write("original contents");
        }

        when(metadataService.getRunningJarPath()).thenReturn(tempJar.getAbsolutePath());
        when(metadataService.getRunningJarChecksum()).thenReturn("different-hash-value-representing-stale-code");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            validator.validate("test-deploy-id", logService);
        });

        assertTrue(ex.getMessage().contains("Runtime Integrity Violation"));
        assertTrue(ex.getMessage().contains("does not match the latest deployed JAR on disk"));
    }

    /**
     * Test Case 3: When duplicate processes are detected on the host system, validation throws an exception.
     */
    @Test
    public void testDuplicateProcessesThrowsException() {
        String currentPid = "12345";
        when(metadataService.getPid()).thenReturn(currentPid);

        when(processLookupService.getRunningBackendProcesses()).thenReturn(
                List.of(
                        new ProcessLookupService.ProcessInfo(currentPid, "java -jar ..."),
                        new ProcessLookupService.ProcessInfo("99999", "java -jar autopilot-backend-0.0.1-SNAPSHOT.jar")
                )
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            validator.validate("test-deploy-id", logService);
        });

        assertTrue(ex.getMessage().contains("Runtime Integrity Violation: Stale/duplicate backend JVM processes detected!"));
        assertTrue(ex.getMessage().contains("PIDs: [99999]"));
    }
}
