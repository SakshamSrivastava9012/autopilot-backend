package com.autopilot.service.deployment.v5.migration.matrix;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Maps every legacy component to its V5 single-engine replacement.
 *
 * @since V5.5 — ADR-014
 */
@Service
public class ReplacementMatrix {

    private final List<MappingEntry> mappings;

    public ReplacementMatrix() {
        List<MappingEntry> entries = new ArrayList<>();
        entries.add(new MappingEntry("DeploymentPipelineService", "DeploymentRuntimeEngineV5", "MIGRATED_VIA_ADAPTER", "Target removal: V6.0"));
        entries.add(new MappingEntry("HealthCheckService", "HealthNegotiationEngineV5", "MIGRATED_VIA_ADAPTER", "Target removal: V6.0"));
        entries.add(new MappingEntry("UniversalNginxGenerator", "ReverseProxyEngineV5", "MIGRATED_VIA_ADAPTER", "Target removal: V6.0"));
        entries.add(new MappingEntry("AssetPatcherService", "AssetRouter", "MIGRATED_VIA_ADAPTER", "Target removal: V6.0"));
        entries.add(new MappingEntry("EnvironmentResolver", "EnvironmentInjectionEngineV5", "MIGRATED_VIA_ADAPTER", "Target removal: V6.0"));
        entries.add(new MappingEntry("DeploymentValidationSuite", "RuntimeVerificationPlatformV5", "MIGRATED_VIA_ADAPTER", "Target removal: V6.0"));
        entries.add(new MappingEntry("DependencyProvisionService", "DependencyProvisionEngineV5", "MIGRATED_VIA_ADAPTER", "Target removal: V6.0"));
        this.mappings = Collections.unmodifiableList(entries);
    }

    public List<MappingEntry> getMappings() {
        return mappings;
    }

    @Value
    @Builder
    public static class MappingEntry {
        String legacyComponent;
        String v5Replacement;
        String migrationStatus;
        String removalPlan;
    }
}
