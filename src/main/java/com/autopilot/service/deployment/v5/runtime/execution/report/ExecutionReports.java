package com.autopilot.service.deployment.v5.runtime.execution.report;

import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.DeploymentFailureReport;
import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.ImageOptimizationReport;
import com.autopilot.service.deployment.v5.runtime.execution.metrics.ExecutionMetricsSnapshot;
import com.autopilot.service.deployment.v5.runtime.execution.stream.ExecutionLogStreamEvent;
import com.autopilot.service.deployment.v5.runtime.execution.timeline.ExecutionTimelinePhase;
import com.autopilot.service.deployment.v5.runtime.execution.tracker.ProgressSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public class ExecutionReports {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeploymentProgressReport {
        private String sessionId;
        private ProgressSnapshot progress;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeploymentTimelineReport {
        private String sessionId;
        @Builder.Default
        private List<ExecutionTimelinePhase> phases = new ArrayList<>();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeploymentMetricsReport {
        private String sessionId;
        private ExecutionMetricsSnapshot metrics;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeploymentDiagnosticsReport {
        private String sessionId;
        private boolean healthy;
        private DeploymentFailureReport failureReport;
        private ImageOptimizationReport imageOptimizationReport;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeploymentLogReport {
        private String sessionId;
        private int totalLogLines;
        @Builder.Default
        private List<ExecutionLogStreamEvent> logs = new ArrayList<>();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeploymentExecutionReport {
        private String sessionId;
        private boolean success;
        private String stage;
        private DeploymentProgressReport progressReport;
        private DeploymentTimelineReport timelineReport;
        private DeploymentMetricsReport metricsReport;
        private DeploymentDiagnosticsReport diagnosticsReport;
        private DeploymentLogReport logReport;
    }
}
