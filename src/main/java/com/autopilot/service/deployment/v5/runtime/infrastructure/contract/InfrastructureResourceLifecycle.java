package com.autopilot.service.deployment.v5.runtime.infrastructure.contract;

/**
 * Lifecycle states of an infrastructure resource.
 *
 * @since V5.4 — ADR-008
 */
public enum InfrastructureResourceLifecycle {
    CREATED,
    PROVISIONING,
    AVAILABLE,
    VALIDATING,
    READY,
    FAILED,
    ROLLBACK,
    ROLLED_BACK
}
