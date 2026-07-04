package com.autopilot.service.deployment.validation;

import java.util.List;

public interface FrameworkStrategy {

    List<String> expectedManifestFiles();

    BuildCommand buildCommand();

    DockerStrategy dockerStrategy();

    int containerPort();

    String healthPath();

    String protocol();

    List<Integer> expectedStatusCodes();

    int startupTimeout();

    int retryPolicy();

    /**
     * Framework-specific log patterns that confirm the application has reached READY state.
     * HTTP probing should NOT begin until at least one of these patterns is detected in logs.
     */
    default List<String> logReadinessMarkers() {
        return List.of("Started", "Listening", "Ready", "Server running", "Accepting connections");
    }

    /**
     * Framework-specific log patterns that indicate a FATAL startup crash.
     * When detected, startup verification should stop immediately and generate a crash report.
     */
    default List<String> logCrashMarkers() {
        return List.of("Exception", "FATAL", "Error:", "Shutting down", "Address already in use", "Port already in use");
    }

    /**
     * Prioritized list of health endpoints to probe. Tried in order.
     * Falls back to TCP socket check if all fail.
     */
    default List<String> healthEndpoints() {
        return List.of(healthPath(), "/");
    }

    /**
     * Critical environment variables that must be set for this framework.
     * Verified immediately after container start, before health probing.
     */
    default List<String> criticalEnvVars() {
        return List.of();
    }

}

