package com.autopilot.service.deployment.intelligence.v5.detector.impl;

import com.autopilot.service.deployment.intelligence.v5.detector.DetectorResultV5;
import com.autopilot.service.deployment.intelligence.v5.detector.RepositoryDetector;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects secret and credential references in the repository.
 * Never reads, stores, or injects actual credential values — only flags their status.
 */
@Component
public class SecretDetectorV5 implements RepositoryDetector {
    @Override public String name() { return "SecretDetector"; }
    @Override public String version() { return "5.0.0"; }

    @Override
    public List<DetectorResultV5> detect(String repositoryPath) {
        List<DetectorResultV5> results = new ArrayList<>();
        File root = new File(repositoryPath);

        // Check for .env files that may contain secrets
        for (String envFile : new String[]{".env", ".env.local", ".env.production", ".env.example"}) {
            if (new File(root, envFile).exists()) {
                results.add(DetectorResultV5.builder()
                        .detectorName(name()).category("SECRET").key("ENV_FILE_" + envFile.toUpperCase().replace(".", "_"))
                        .value(envFile).confidence(0.80).provenance(envFile)
                        .evidence(Arrays.asList("Environment file that may contain secrets: " + envFile))
                        .build());
            }
        }

        return results;
    }
}
