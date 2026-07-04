package com.autopilot.service.deployment.v5.runtime.dependency.contract;

/**
 * Runtime lifecycle states for dependencies.
 *
 * @since V5.4 — ADR-009
 */
public enum DependencyLifecycle {
    DISCOVERED,
    PROVISIONING,
    STARTING,
    READY,
    HEALTHY,
    FAILED,
    ROLLBACK,
    DESTROYED
}
