package com.autopilot.service.deployment.v5.runtime.startup.negotiation;

/**
 * Structured startup failure types.
 *
 * @since V5.4 — ADR-011
 */
public enum StartupFailureType {
    CONTAINER_START_FAILED(true),
    PROCESS_CRASHED(true),
    PORT_NOT_BOUND(true),
    READINESS_TIMEOUT(true),
    HEALTH_TIMEOUT(true),
    OOM_KILLED(false),
    EXIT_CODE(false),
    CONFIGURATION_ERROR(false),
    DEPENDENCY_NOT_READY(true);

    private final boolean retryable;

    StartupFailureType(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
