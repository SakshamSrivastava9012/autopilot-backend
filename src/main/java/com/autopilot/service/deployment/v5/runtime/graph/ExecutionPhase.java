package com.autopilot.service.deployment.v5.runtime.graph;

/**
 * Execution phases for deployment nodes.
 *
 * @since V5.4 — ADR-007
 */
public enum ExecutionPhase {
    INITIALIZATION,
    INFRASTRUCTURE,
    DEPENDENCIES,
    CREDENTIALS,
    CONTAINERS,
    NETWORK,
    STARTUP,
    REVERSE_PROXY,
    VALIDATION,
    FINALIZATION,
    KUBERNETES,
    SERVICE_MESH,
    MULTI_CLUSTER
}
