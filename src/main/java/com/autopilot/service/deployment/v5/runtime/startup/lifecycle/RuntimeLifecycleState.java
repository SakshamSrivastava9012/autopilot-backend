package com.autopilot.service.deployment.v5.runtime.startup.lifecycle;

/**
 * Strict state machine for container startup and runtime lifecycle.
 * No intermediate state may be skipped.
 *
 * @since V5.4 — ADR-011
 */
public enum RuntimeLifecycleState {
    IMAGE_READY,
    CONTAINER_CREATED,
    CONTAINER_RUNNING,
    PROCESS_RUNNING,
    PORT_DISCOVERY,
    READINESS_NEGOTIATION,
    READINESS_CONFIRMED,
    HEALTH_NEGOTIATION,
    HEALTH_CONFIRMED,
    READY,
    STABLE,
    FAILED,
    ROLLBACK
}
