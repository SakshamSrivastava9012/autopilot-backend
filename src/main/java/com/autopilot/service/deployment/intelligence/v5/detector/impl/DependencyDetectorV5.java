package com.autopilot.service.deployment.intelligence.v5.detector.impl;

import com.autopilot.service.deployment.intelligence.v5.detector.DetectorResultV5;
import com.autopilot.service.deployment.intelligence.v5.detector.RepositoryDetector;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects database and external service dependencies from configuration files.
 * Detection only — never provisions or connects.
 */
@Component
public class DependencyDetectorV5 implements RepositoryDetector {
    @Override public String name() { return "DependencyDetector"; }
    @Override public String version() { return "5.0.0"; }

    @Override
    public List<DetectorResultV5> detect(String repositoryPath) {
        List<DetectorResultV5> results = new ArrayList<>();
        File root = new File(repositoryPath);

        // Spring Boot datasource detection
        if (new File(root, "application.properties").exists() || new File(root, "application.yml").exists()
                || new File(root, "src/main/resources/application.properties").exists()
                || new File(root, "src/main/resources/application.yml").exists()) {
            results.add(DetectorResultV5.builder()
                    .detectorName(name()).category("DEPENDENCY").key("DATABASE")
                    .value("detected_from_spring_config").confidence(0.80).provenance("application.yml / application.properties")
                    .evidence(Arrays.asList("Spring datasource configuration file exists")).build());
        }

        // .env based detection
        for (String envFile : new String[]{".env", ".env.local", ".env.production"}) {
            if (new File(root, envFile).exists()) {
                results.add(DetectorResultV5.builder()
                        .detectorName(name()).category("ENV_SOURCE").key(envFile)
                        .confidence(0.90).provenance(envFile)
                        .evidence(Arrays.asList("Environment file '" + envFile + "' found")).build());
            }
        }

        // Docker Compose dependency detection
        if (new File(root, "docker-compose.yml").exists() || new File(root, "compose.yaml").exists()) {
            results.add(DetectorResultV5.builder()
                    .detectorName(name()).category("DEPENDENCY").key("COMPOSE_SERVICES")
                    .confidence(0.85).provenance("docker-compose.yml / compose.yaml")
                    .evidence(Arrays.asList("Compose file may define database and cache services")).build());
        }

        return results;
    }
}
