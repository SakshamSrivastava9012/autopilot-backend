package com.autopilot.service.deployment.v5.build;

import com.autopilot.service.deployment.intelligence.v5.model.RepositoryModelV5;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * The Universal Build Engine.
 * Orchestrates build strategy resolution and build execution.
 * Produces immutable BuildArtifact and BuildReport.
 *
 * This engine builds images but never deploys them.
 *
 * @since V5.3 — ADR-006
 */
@Service
public class BuildEngineV5 {

    private final BuildStrategyResolver strategyResolver;

    public BuildEngineV5(BuildStrategyResolver strategyResolver) {
        this.strategyResolver = strategyResolver;
    }

    /**
     * Resolve build strategy and execute build.
     * Returns immutable BuildArtifact.
     */
    public BuildResult build(RepositoryModelV5 model, String deploymentId) {
        System.out.println("🏗️ Build Engine V5 — Starting build for deployment: " + deploymentId);
        long buildStart = System.currentTimeMillis();

        BuildPlan plan = strategyResolver.resolve(model);
        System.out.println("   Strategy: " + plan.getStrategy() + " (confidence=" + plan.getConfidence() + "%)");

        // In a real implementation, this would invoke Docker build / Buildpack / Maven etc.
        // For now, we produce the metadata contract.
        String imageName = "deployrix/" + deploymentId + ":latest";

        long buildEnd = System.currentTimeMillis();

        BuildArtifact artifact = BuildArtifact.builder()
                .imageName(imageName)
                .imageDigest("sha256:" + UUID.randomUUID().toString().replace("-", "").substring(0, 32))
                .imageId(UUID.randomUUID().toString().substring(0, 12))
                .runtime(plan.getBaseImage() != null ? plan.getBaseImage() : "custom")
                .exposedPorts(plan.getExposedPorts())
                .entrypoint(plan.getStartCommand())
                .cmd(plan.getBuildCommand())
                .labels(plan.getLabels())
                .buildLogs(Collections.emptyList())
                .buildDurationMs(buildEnd - buildStart)
                .imageSizeBytes(0)
                .warnings(plan.getWarnings())
                .build();

        BuildReport report = BuildReport.builder()
                .serviceId(deploymentId)
                .strategy(plan.getStrategy())
                .success(true)
                .durationMs(buildEnd - buildStart)
                .imageSizeBytes(0)
                .warnings(plan.getWarnings())
                .errors(Collections.emptyList())
                .build();

        return new BuildResult(plan, artifact, report);
    }

    @lombok.Value
    public static class BuildResult {
        BuildPlan plan;
        BuildArtifact artifact;
        BuildReport report;
    }
}
