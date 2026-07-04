package com.autopilot.service.deployment.v5.runtime.graph;

/**
 * State machine transitions for execution graph nodes.
 *
 * @since V5.4 — ADR-007
 */
public enum NodeState {
    CREATED,
    WAITING,
    READY,
    RUNNING,
    VERIFYING,
    COMPLETED,
    FAILED,
    ROLLBACK,
    ROLLED_BACK
}
