package com.autopilot.service.deployment.intelligence.v5;

import com.autopilot.service.deployment.intelligence.v5.detector.DetectorRegistryV5;
import com.autopilot.service.deployment.intelligence.v5.detector.DetectorResultV5;
import com.autopilot.service.deployment.intelligence.v5.detector.RepositoryDetector;
import com.autopilot.service.deployment.intelligence.v5.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * The V5 Repository Intelligence Engine.
 *
 * This is the Deployrix equivalent of Kubernetes API resource discovery.
 * Its sole responsibility is observation — it scans the repository exactly once
 * and produces an immutable RepositoryModelV5 that every downstream subsystem consumes.
 *
 * It NEVER provisions, builds, deploys, injects, or mutates anything.
 *
 * @since V5 — ADR-004
 */
@Service
public class RepositoryIntelligenceEngineV5 {

    private final DetectorRegistryV5 registry;

    public RepositoryIntelligenceEngineV5(DetectorRegistryV5 registry) {
        this.registry = registry;
    }

    /**
     * Performs a single, deterministic scan of the repository.
     * The resulting RepositoryModelV5 is immutable and must never be regenerated
     * during the same deployment lifecycle.
     */
    public RepositoryModelV5 analyze(String repositoryPath, String commitHash, String branch, String repositoryUrl) {
        System.out.println("🔬 V5 Repository Intelligence Engine — Starting deterministic scan...");
        long scanStart = System.currentTimeMillis();

        // ─── Execute all detectors ─────────────────────────────
        Map<String, Long> detectorTimings = new LinkedHashMap<>();
        List<DetectorResultV5> allResults = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (RepositoryDetector detector : registry.getDetectors()) {
            long detectorStart = System.currentTimeMillis();
            try {
                List<DetectorResultV5> results = detector.detect(repositoryPath);
                allResults.addAll(results);
            } catch (Exception e) {
                warnings.add("Detector [" + detector.name() + "] failed: " + e.getMessage());
                System.err.println("⚠️ Detector " + detector.name() + " failed: " + e.getMessage());
            }
            detectorTimings.put(detector.name(), System.currentTimeMillis() - detectorStart);
        }

        // ─── Merge results into typed collections ──────────────
        Set<String> languages = new LinkedHashSet<>();
        Set<String> frameworks = new LinkedHashSet<>();
        Set<String> capabilities = new LinkedHashSet<>();
        List<ServiceDescriptorV5> services = new ArrayList<>();
        List<DependencyDefinition> dependencies = new ArrayList<>();
        List<AssetDefinition> assets = new ArrayList<>();
        List<SecretDefinition> secrets = new ArrayList<>();

        for (DetectorResultV5 result : allResults) {
            if (result.getConfidence() < 0.5) continue; // Confidence threshold

            switch (result.getCategory()) {
                case "LANGUAGE":
                    languages.add(result.getKey());
                    break;
                case "FRAMEWORK":
                    frameworks.add(result.getKey());
                    break;
                case "CAPABILITY":
                    capabilities.add(result.getKey());
                    break;
                case "SERVICE":
                    services.add(ServiceDescriptorV5.builder()
                            .serviceId(UUID.randomUUID().toString().substring(0, 8))
                            .name(result.getKey())
                            .root(result.getValue())
                            .build());
                    break;
                case "DEPENDENCY":
                    dependencies.add(DependencyDefinition.builder()
                            .type(result.getKey())
                            .name(result.getKey().toLowerCase())
                            .confidence(result.getConfidence())
                            .source(result.getProvenance())
                            .evidence(result.getEvidence())
                            .required(true)
                            .build());
                    break;
                case "ASSET":
                    assets.add(AssetDefinition.builder()
                            .path(result.getValue())
                            .type(result.getKey().toUpperCase())
                            .build());
                    break;
                case "SECRET":
                    secrets.add(SecretDefinition.builder()
                            .key(result.getKey())
                            .status("ENVIRONMENT")
                            .source(result.getProvenance())
                            .evidence(result.getEvidence())
                            .build());
                    break;
                default:
                    break;
            }
        }

        long scanEnd = System.currentTimeMillis();

        // ─── Build immutable RepositoryModelV5 ─────────────────
        RepositoryModelV5 model = RepositoryModelV5.builder()
                .schemaVersion("5.0.0")
                .engineVersion("5.0.0")
                .generatedAt(scanEnd)
                .repositoryHash(UUID.nameUUIDFromBytes(repositoryPath.getBytes()).toString())
                .commitHash(commitHash)
                .branch(branch)
                .repositoryUrl(repositoryUrl)
                .workspace(repositoryPath)
                .languages(Collections.unmodifiableSet(languages))
                .frameworks(Collections.unmodifiableSet(frameworks))
                .packageManagers(Collections.unmodifiableSet(new LinkedHashSet<>()))
                .buildSystems(Collections.unmodifiableSet(new LinkedHashSet<>()))
                .capabilities(Collections.unmodifiableSet(capabilities))
                .services(Collections.unmodifiableList(services))
                .dependencies(Collections.unmodifiableList(dependencies))
                .environmentDefinitions(Collections.unmodifiableList(new ArrayList<>()))
                .assets(Collections.unmodifiableList(assets))
                .routes(Collections.unmodifiableList(new ArrayList<>()))
                .secrets(Collections.unmodifiableList(secrets))
                .metadata(Collections.unmodifiableMap(new LinkedHashMap<>()))
                .warnings(Collections.unmodifiableList(warnings))
                .discoveryTimeline(DiscoveryTimeline.builder()
                        .totalDurationMs(scanEnd - scanStart)
                        .detectorDurations(Collections.unmodifiableMap(detectorTimings))
                        .scanStartEpoch(scanStart)
                        .scanEndEpoch(scanEnd)
                        .build())
                .build();

        System.out.println("✅ V5 Repository Analysis Complete in " + (scanEnd - scanStart) + "ms");
        System.out.println("   Languages:    " + languages);
        System.out.println("   Frameworks:   " + frameworks);
        System.out.println("   Capabilities: " + capabilities);
        System.out.println("   Services:     " + services.size());
        System.out.println("   Dependencies: " + dependencies.size());
        System.out.println("   Assets:       " + assets.size());
        System.out.println("   Secrets:      " + secrets.size());
        System.out.println("   Warnings:     " + warnings.size());

        return model;
    }

    /**
     * Generates a concise discovery report for the Deployrix dashboard.
     */
    public RepositoryDiscoveryReport generateReport(RepositoryModelV5 model) {
        return RepositoryDiscoveryReport.builder()
                .languages(model.getLanguages())
                .frameworks(model.getFrameworks())
                .capabilities(model.getCapabilities())
                .serviceCount(model.getServices().size())
                .dependencyCount(model.getDependencies().size())
                .assetDirectoryCount(model.getAssets().size())
                .routeCount(model.getRoutes().size())
                .secretCount(model.getSecrets().size())
                .warningCount(model.getWarnings().size())
                .discoveryDurationMs(model.getDiscoveryTimeline().getTotalDurationMs())
                .detectorTimings(model.getDiscoveryTimeline().getDetectorDurations())
                .warnings(model.getWarnings())
                .build();
    }
}
