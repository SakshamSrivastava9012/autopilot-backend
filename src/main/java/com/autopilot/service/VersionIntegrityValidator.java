package com.autopilot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VersionIntegrityValidator {

    private final BuildMetadataService metadataService;
    private final ProcessLookupService processLookupService;

    public void validate(String did, com.autopilot.service.log.DeploymentLogService logService) {
        String currentPid = metadataService.getPid();

        // 1. Log all metadata to the deployment's log stream
        logService.info(did, "ANALYZING", "--- Runtime Version Integrity Diagnostics ---");
        logService.info(did, "ANALYZING", "Backend Version: " + metadataService.getVersion());
        logService.info(did, "ANALYZING", "Git Commit: " + metadataService.getGitCommit());
        logService.info(did, "ANALYZING", "Build Timestamp: " + metadataService.getBuildTimestamp());
        logService.info(did, "ANALYZING", "JVM PID: " + currentPid);
        logService.info(did, "ANALYZING", "Application Start Time: " + new java.util.Date(metadataService.getStartTime()));
        logService.info(did, "ANALYZING", "---------------------------------------------");

        // 2. Verify exactly one backend process is running (no orphans or duplicates)
        List<ProcessLookupService.ProcessInfo> javaProcesses = processLookupService.getRunningBackendProcesses();

        long otherInstancesCount = javaProcesses.stream()
                .filter(p -> !p.getPid().equals(currentPid))
                .count();

        if (otherInstancesCount > 0) {
            String otherPids = javaProcesses.stream()
                    .filter(p -> !p.getPid().equals(currentPid))
                    .map(p -> p.getPid())
                    .collect(Collectors.joining(", "));
            throw new RuntimeException("Runtime Integrity Violation: Stale/duplicate backend JVM processes detected! PIDs: [" + otherPids + "]");
        }

        // 3. Verify JAR checksum matches (running code vs disk code)
        String jarPath = metadataService.getRunningJarPath();
        if (jarPath != null) {
            File file = new File(jarPath);
            if (file.exists() && !file.isDirectory()) {
                String diskChecksum = calculateSha256(file);
                String runningChecksum = metadataService.getRunningJarChecksum();
                if (!runningChecksum.equals(diskChecksum)) {
                    throw new RuntimeException("Runtime Integrity Violation: The running JAR checksum (" + runningChecksum + 
                            ") does not match the latest deployed JAR on disk (" + diskChecksum + "). Re-deployment or restart required.");
                }
            }
        }
        logService.info(did, "ANALYZING", "✅ Runtime Integrity Validation Passed");
    }

    private String calculateSha256(File file) {
        try (InputStream fis = new java.io.FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
