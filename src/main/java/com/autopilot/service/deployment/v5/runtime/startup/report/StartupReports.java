package com.autopilot.service.deployment.v5.runtime.startup.report;

import com.autopilot.service.deployment.v5.runtime.startup.lifecycle.RuntimeLifecycleState;
import com.autopilot.service.deployment.v5.runtime.startup.negotiation.StartupFailureType;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Dashboard-ready startup reports.
 *
 * @since V5.4 — ADR-011
 */
public class StartupReports {

    @Value
    @Builder
    public static class StartupReport {
        String serviceId;
        String containerId;
        boolean success;
        long totalStartupDurationMs;
        RuntimeLifecycleState finalState;
        StartupFailureType failureType;
        List<String> logs;
        List<String> warnings;
    }

    @Value
    @Builder
    public static class ReadinessReport {
        String containerId;
        boolean ready;
        String strategy;
        String endpoint;
        long readinessDurationMs;
        List<String> diagnostics;
    }

    @Value
    @Builder
    public static class HealthReport {
        String containerId;
        boolean healthy;
        String endpoint;
        int statusCode;
        long responseTimeMs;
        List<String> diagnostics;
    }

    @Value
    @Builder
    public static class LifecycleReport {
        String containerId;
        List<String> stateTransitions;
        List<String> eventsEmitted;
    }
}
