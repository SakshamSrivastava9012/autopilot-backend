package com.autopilot.service.deployment.v5.runtime.startup.lifecycle;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable runtime lifecycle events emitted during container startup.
 *
 * @since V5.4 — ADR-011
 */
public class RuntimeLifecycleEvents {

    @Value
    @Builder
    public static class ContainerCreated {
        String containerId;
        String image;
        long timestampEpoch;
    }

    @Value
    @Builder
    public static class ProcessStarted {
        String containerId;
        long pid;
        long timestampEpoch;
    }

    @Value
    @Builder
    public static class PortBound {
        String containerId;
        int port;
        long timestampEpoch;
    }

    @Value
    @Builder
    public static class ReadinessConfirmed {
        String containerId;
        String endpoint;
        long durationMs;
        long timestampEpoch;
    }

    @Value
    @Builder
    public static class HealthConfirmed {
        String containerId;
        String endpoint;
        int statusCode;
        long timestampEpoch;
    }

    @Value
    @Builder
    public static class ApplicationReady {
        String containerId;
        String serviceId;
        long totalStartupDurationMs;
        long timestampEpoch;
    }

    @Value
    @Builder
    public static class ApplicationStable {
        String containerId;
        long uptimeMs;
        long timestampEpoch;
    }

    @Value
    @Builder
    public static class StartupFailed {
        String containerId;
        String failureType;
        String reason;
        long timestampEpoch;
    }
}
