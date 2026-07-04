package com.autopilot.service.deployment.v5.runtime.environment.report;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Dashboard-ready environment reports.
 *
 * @since V5.4 — ADR-010
 */
public class EnvironmentReports {

    @Value
    @Builder
    public static class EnvironmentInjectionReport {
        String environmentId;
        String framework;
        boolean success;
        long durationMs;
        int totalVariablesInjected;
        int secretsResolved;
        List<String> logs;
        List<String> warnings;
    }

    @Value
    @Builder
    public static class FrameworkMappingReport {
        String framework;
        int connectionCount;
        Map<String, String> mappedKeys;
    }

    @Value
    @Builder
    public static class ConfigurationSanitizationReport {
        int variablesRemoved;
        List<String> removedReasonList;
    }

    @Value
    @Builder
    public static class SecretResolutionReport {
        int secretsResolvedCount;
        List<String> resolvedSecretKeys;
    }
}
