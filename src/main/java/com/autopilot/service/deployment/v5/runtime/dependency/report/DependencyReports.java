package com.autopilot.service.deployment.v5.runtime.dependency.report;

import com.autopilot.service.deployment.v5.runtime.dependency.contract.DependencyFailureType;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.DependencyLifecycle;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Dashboard-ready dependency reports.
 *
 * @since V5.4 — ADR-009
 */
public class DependencyReports {

    @Value
    @Builder
    public static class DependencyProvisionReport {
        String dependencyId;
        String provider;
        boolean success;
        long durationMs;
        DependencyLifecycle status;
        DependencyFailureType failureType;
        List<String> logs;
        List<String> warnings;
    }

    @Value
    @Builder
    public static class DependencyHealthReport {
        String dependencyId;
        boolean healthy;
        String healthStrategy;
        long responseTimeMs;
        List<String> diagnostics;
    }

    @Value
    @Builder
    public static class CredentialResolutionReport {
        String dependencyId;
        String provider;
        String generatedBy;
        boolean rotationSupported;
        String secretReference;
    }

    @Value
    @Builder
    public static class DependencyRollbackReport {
        String dependencyId;
        boolean success;
        String provider;
        int resourcesDestroyed;
        int resourcesPreserved;
        List<String> logs;
    }
}
