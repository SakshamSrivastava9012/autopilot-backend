package com.autopilot.service.deployment.v5.runtime.dependency.contract;

/**
 * Structured failure classification for dependency operations.
 *
 * @since V5.4 — ADR-009
 */
public enum DependencyFailureType {
    DEPENDENCY_START_FAILED(true),
    DEPENDENCY_HEALTH_TIMEOUT(true),
    CREDENTIAL_NOT_AVAILABLE(false),
    PROVIDER_ERROR(true),
    VERSION_UNSUPPORTED(false),
    ROLLBACK_FAILED(false),
    VALIDATION_FAILED(false);

    private final boolean retryable;

    DependencyFailureType(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
