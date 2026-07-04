package com.autopilot.service.deployment.v5.runtime.contract;

import lombok.Value;

/**
 * Immutable events for the deployment execution lifecycle.
 *
 * @since V5.4 — ADR-007
 */
public class RuntimeEvents {

    @Value
    public static class ExecutionStarted {
        String deploymentId;
        long timestampEpoch;
        int totalNodes;
    }

    @Value
    public static class NodeReady {
        String deploymentId;
        String nodeId;
        String nodeName;
        long timestampEpoch;
    }

    @Value
    public static class NodeCompleted {
        String deploymentId;
        String nodeId;
        String nodeName;
        long durationMs;
        long timestampEpoch;
    }

    @Value
    public static class NodeFailed {
        String deploymentId;
        String nodeId;
        String nodeName;
        String errorMessage;
        long timestampEpoch;
    }

    @Value
    public static class RollbackStarted {
        String deploymentId;
        String failedNodeId;
        long timestampEpoch;
    }

    @Value
    public static class RollbackCompleted {
        String deploymentId;
        int rolledBackNodesCount;
        long timestampEpoch;
    }

    @Value
    public static class ExecutionFinished {
        String deploymentId;
        boolean success;
        long totalDurationMs;
        long timestampEpoch;
    }
}
