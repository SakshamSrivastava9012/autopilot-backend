package com.autopilot.service.deployment.v5.runtime.report;

import com.autopilot.service.deployment.v5.runtime.graph.NodeState;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Structured runtime reports for dashboard visualization and diagnostics.
 *
 * @since V5.4 — ADR-007
 */
public class ExecutionReports {

    @Value
    @Builder
    public static class ExecutionReport {
        String deploymentId;
        boolean success;
        long totalDurationMs;
        int totalNodes;
        int completedNodes;
        int failedNodes;
        int rolledBackNodes;
        List<String> warnings;
        List<String> errors;
    }

    @Value
    @Builder
    public static class TimelineReport {
        String deploymentId;
        long totalDurationMs;
        int totalEvents;
        Map<String, Long> nodeDurations;
        List<String> threadUsage;
    }

    @Value
    @Builder
    public static class RuntimeSchedulingReport {
        String deploymentId;
        int nodeCount;
        Map<NodeState, Integer> nodeStateCounts;
        List<String> parallelBatches;
        List<String> schedulingLogs;
    }
}
