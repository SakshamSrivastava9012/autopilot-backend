package com.autopilot.service.deployment.v5.runtime.infrastructure.report;

import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.InfrastructureFailureType;
import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.InfrastructureResourceLifecycle;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Dashboard-ready reports for infrastructure provisioning phases.
 *
 * @since V5.4 — ADR-008
 */
public class InfrastructureReports {

    @Value
    @Builder
    public static class InfrastructureProvisionReport {
        String resourceId;
        String provider;
        boolean success;
        long durationMs;
        InfrastructureResourceLifecycle status;
        InfrastructureFailureType failureType;
        List<String> logs;
        List<String> warnings;
    }

    @Value
    @Builder
    public static class InfrastructureValidationReport {
        String resourceId;
        boolean valid;
        String provider;
        String statusMessage;
        List<String> diagnostics;
    }

    @Value
    @Builder
    public static class InfrastructureRollbackReport {
        String resourceId;
        boolean success;
        String provider;
        int resourcesDeleted;
        int resourcesPreserved; // USER / EXTERNAL owned
        List<String> logs;
    }

    @Value
    @Builder
    public static class InfrastructureSnapshotReport {
        int totalResources;
        int activeResources;
        Map<String, Integer> resourceCountByProvider;
        List<String> activeIdentifiers;
    }
}
