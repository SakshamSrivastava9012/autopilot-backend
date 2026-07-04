package com.autopilot.service.deployment.v5.runtime.infrastructure.contract;

/**
 * Structured failure types for infrastructure operations.
 *
 * @since V5.4 — ADR-008
 */
public enum InfrastructureFailureType {
    RESOURCE_NOT_FOUND(false),
    PERMISSION_DENIED(false),
    REGION_UNSUPPORTED(false),
    QUOTA_EXCEEDED(false),
    NETWORK_FAILURE(true),
    API_FAILURE(true),
    TIMEOUT(true),
    ROLLBACK_FAILURE(false),
    VALIDATION_FAILED(false);

    private final boolean retryable;

    InfrastructureFailureType(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
