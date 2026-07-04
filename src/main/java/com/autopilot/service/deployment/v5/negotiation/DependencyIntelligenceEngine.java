package com.autopilot.service.deployment.v5.negotiation;

import com.autopilot.service.deployment.intelligence.v5.model.DependencyDefinition;
import com.autopilot.service.deployment.intelligence.v5.model.RepositoryModelV5;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Interprets dependency observations from RepositoryModelV5 into structured intelligence.
 * Pure data transformation — no network I/O, no provisioning.
 *
 * @since V5.2
 */
@Service
public class DependencyIntelligenceEngine {

    private final ConfigurationClassifier classifier;

    public DependencyIntelligenceEngine(ConfigurationClassifier classifier) {
        this.classifier = classifier;
    }

    /**
     * Analyze all dependencies from the RepositoryModelV5 and produce classified intelligence.
     * No filesystem access. No provisioning.
     */
    public List<DependencyIntelligence> analyze(RepositoryModelV5 model) {
        System.out.println("🧠 Dependency Intelligence Engine — Analyzing " + model.getDependencies().size() + " dependencies...");

        List<DependencyIntelligence> results = new ArrayList<>();

        for (DependencyDefinition dep : model.getDependencies()) {
            EndpointClassification classification = classifier.classify(dep.getConnectionHint());
            boolean isDev = classifier.isDevelopmentEndpoint(classification);
            boolean isProd = classifier.isProductionEndpoint(classification);

            results.add(DependencyIntelligence.builder()
                    .type(dep.getType())
                    .name(dep.getName())
                    .connectionHint(dep.getConnectionHint())
                    .endpointClassification(classification)
                    .isDevelopmentEndpoint(isDev)
                    .isProductionEndpoint(isProd)
                    .detectedProvider(dep.getDetectedProvider())
                    .detectedVersion(dep.getDetectedVersion())
                    .confidence(dep.getConfidence())
                    .source(dep.getSource())
                    .build());
        }

        return results;
    }

    @lombok.Value
    @lombok.Builder
    public static class DependencyIntelligence {
        String type;
        String name;
        String connectionHint;
        EndpointClassification endpointClassification;
        boolean isDevelopmentEndpoint;
        boolean isProductionEndpoint;
        String detectedProvider;
        String detectedVersion;
        double confidence;
        String source;
    }
}
