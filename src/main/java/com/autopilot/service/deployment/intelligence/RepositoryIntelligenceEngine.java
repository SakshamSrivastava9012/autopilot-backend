package com.autopilot.service.deployment.intelligence;

import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Orchestrates all repository scanning.
 * This is the ONLY component allowed to perform raw filesystem discovery prior to build.
 */
@Service
public class RepositoryIntelligenceEngine {

    private final DetectorRegistry registry;

    public RepositoryIntelligenceEngine(DetectorRegistry registry) {
        this.registry = registry;
    }

    public RepositoryModel analyze(String repositoryPath, String repositoryHash) {
        System.out.println("🔍 Starting Repository Intelligence Scan for: " + repositoryPath);

        // In a full implementation, we would check the cache here using repositoryHash.

        RepositoryModel.RepositoryModelBuilder builder = RepositoryModel.builder()
                .repositoryPath(repositoryPath)
                .schemaVersion("v1.0")
                .generatedAt(System.currentTimeMillis())
                .engineVersion("1.0.0")
                .repositoryHash(repositoryHash)
                .capabilities(new java.util.HashSet<>())
                .staticAssetDirectories(new java.util.ArrayList<>())
                .databaseDependencies(new java.util.ArrayList<>())
                .healthEndpoints(new java.util.ArrayList<>())
                .oauthEndpoints(new java.util.ArrayList<>())
                .exposedPorts(new java.util.ArrayList<>())
                .runtimeHints(new java.util.ArrayList<>());

        for (RepositoryScanner scanner : registry.getScanners()) {
            try {
                List<DetectorResult> results = scanner.scan(repositoryPath);
                for (DetectorResult result : results) {
                    mergeResult(builder, result);
                }
            } catch (Exception e) {
                System.err.println("Scanner " + scanner.getClass().getSimpleName() + " failed: " + e.getMessage());
                // Detectors should be resilient; one failing shouldn't crash discovery.
            }
        }

        RepositoryModel model = builder.build();
        System.out.println("✅ Repository Analysis Complete. Discovered Capabilities: " + model.getCapabilities());
        return model;
    }

    private void mergeResult(RepositoryModel.RepositoryModelBuilder builder, DetectorResult result) {
        // High confidence threshold (e.g. > 0.5) can be enforced here.
        if (result.getConfidence() < 0.5) {
            return;
        }

        switch (result.getCategory()) {
            case "CAPABILITY":
                builder.build().getCapabilities().add(result.getKey());
                break;
            case "FRAMEWORK":
                builder.detectedFramework(result.getKey());
                break;
            case "DATABASE":
                builder.build().getDatabaseDependencies().add(result.getKey());
                break;
            case "ASSET_DIR":
                builder.build().getStaticAssetDirectories().add(result.getValue());
                break;
            case "OAUTH":
                builder.build().getOauthEndpoints().add(result.getValue());
                break;
            case "PORT":
                try {
                    builder.build().getExposedPorts().add(Integer.parseInt(result.getValue()));
                } catch (NumberFormatException ignored) {}
                break;
            default:
                // Handle arbitrary discoveries via runtime hints
                builder.build().getRuntimeHints().add(result.getKey() + "=" + result.getValue());
                break;
        }
    }
}
