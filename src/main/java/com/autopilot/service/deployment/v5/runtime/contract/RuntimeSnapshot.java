package com.autopilot.service.deployment.v5.runtime.contract;

import com.autopilot.service.deployment.v5.runtime.graph.ExecutionGraph;
import com.autopilot.service.deployment.v5.runtime.report.ExecutionReports;
import com.autopilot.service.deployment.v5.runtime.timeline.ExecutionTimeline;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of deployment runtime state.
 *
 * @since V5.4 — ADR-007
 */
@Value
@Builder
public class RuntimeSnapshot {
    String deploymentId;
    boolean success;
    ExecutionGraph executionGraph;
    ExecutionTimeline executionTimeline;
    List<String> completedNodes;
    List<String> runningNodes;
    List<String> failedNodes;
    ExecutionReports.ExecutionReport executionReport;
    ExecutionReports.TimelineReport timelineReport;
    ExecutionReports.RuntimeSchedulingReport schedulingReport;
    Map<String, Object> infrastructureReferences;
    Map<String, Object> runtimeReferences;
}
