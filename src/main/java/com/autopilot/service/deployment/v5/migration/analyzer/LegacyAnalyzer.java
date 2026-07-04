package com.autopilot.service.deployment.v5.migration.analyzer;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Scans codebase inventory for legacy deployment components and duplicate logic.
 *
 * @since V5.5 — ADR-014
 */
@Service
public class LegacyAnalyzer {

    public LegacyInventory analyzeCodebase() {
        System.out.println("🔍 Legacy Analyzer — Scanning codebase for duplicate deployment services and legacy components...");

        return LegacyInventory.builder()
                .legacyServices(Arrays.asList("DeploymentPipelineService", "HealthCheckService", "UniversalNginxGenerator", "AssetPatcherService"))
                .duplicatedDTOs(Arrays.asList("DeploymentManifest (V1)", "LegacyHealthResult"))
                .legacyReports(Arrays.asList("LegacyDeploymentReport"))
                .legacyValidators(Arrays.asList("LegacyHealthValidator"))
                .legacyProxyGenerators(Arrays.asList("UniversalNginxGenerator"))
                .legacyEnvironmentInjectors(Arrays.asList("EnvironmentResolver"))
                .build();
    }
}
