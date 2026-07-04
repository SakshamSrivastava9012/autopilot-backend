package com.autopilot.service.deployment.intelligence.v5;

import com.autopilot.dto.*;
import com.autopilot.service.deployment.intelligence.v5.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Transforms an immutable RepositoryModelV5 into an immutable DeploymentManifest.
 *
 * This is a pure data transformation — no filesystem access, no provisioning, no side effects.
 * The resulting DeploymentManifest is consumed by all downstream deployment subsystems.
 *
 * @since V5 — ADR-004
 */
@Service
public class ManifestBuilder {

    /**
     * Converts the V5 RepositoryModel into the platform's DeploymentManifest.
     * This bridge maintains backward compatibility with all existing pipeline stages.
     */
    public DeploymentManifest buildManifest(RepositoryModelV5 model, String deploymentId) {
        System.out.println("📋 Building Deployment Manifest from RepositoryModelV5...");

        // ─── Map V5 services to legacy ServiceDescriptors ─────
        List<ServiceDescriptor> services = model.getServices().stream()
                .map(svc -> ServiceDescriptor.builder()
                        .id(svc.getServiceId())
                        .name(svc.getName())
                        .language(svc.getLanguage())
                        .framework(svc.getFramework())
                        .serviceRoot(svc.getRoot())
                        .role(svc.getRole() != null ? ServiceRole.valueOf(svc.getRole()) : null)
                        .dockerfile(svc.isDockerfileExists() ? svc.getDockerfilePath() : null)
                        .build())
                .collect(Collectors.toList());

        // ─── Map V5 assets ────────────────────────────────────
        List<AssetManifestEntry> assets = model.getAssets().stream()
                .map(a -> AssetManifestEntry.builder()
                        .logicalPath(a.getPath())
                        .containerPath(a.getPath())
                        .publicUrl(a.getPath())
                        .requiresPrefix(false)
                        .cacheable(true)
                        .build())
                .collect(Collectors.toList());

        // ─── Map V5 capabilities ──────────────────────────────
        List<String> capabilities = new ArrayList<>(model.getCapabilities());

        // ─── Map V5 dependencies to database descriptor (legacy compat) ──
        DatabaseDescriptor database = null;
        if (!model.getDependencies().isEmpty()) {
            DependencyDefinition primary = model.getDependencies().get(0);
            database = DatabaseDescriptor.builder()
                    .engine(primary.getType())
                    .provider(primary.getDetectedProvider() != null ? primary.getDetectedProvider() : "UNKNOWN")
                    .requiresProvisioning(true)
                    .build();
        }

        // ─── Build RuntimeReports map for dashboard ───────────
        HashMap<String, Object> runtimeReports = new HashMap<>();
        runtimeReports.put("discoveryTimeline", model.getDiscoveryTimeline());
        runtimeReports.put("repositoryHash", model.getRepositoryHash());
        runtimeReports.put("schemaVersion", model.getSchemaVersion());
        runtimeReports.put("warningCount", model.getWarnings().size());

        DeploymentManifest manifest = DeploymentManifest.builder()
                .deploymentId(deploymentId)
                .application(model.getRepositoryUrl())
                .services(services)
                .assets(assets)
                .capabilities(capabilities)
                .database(database)
                .runtimeReports(runtimeReports)
                .build();

        System.out.println("✅ Deployment Manifest built. Services: " + services.size()
                + ", Assets: " + assets.size()
                + ", Capabilities: " + capabilities.size());

        return manifest;
    }
}
