package com.autopilot.service.deployment.v5.runtime.execution.engine;

import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.DockerPullAnalyzer;
import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.ExecutionDiagnosticsEngine;
import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.ImageOptimizationReport;
import com.autopilot.service.deployment.v5.runtime.execution.events.ExecutionEvent;
import com.autopilot.service.deployment.v5.runtime.execution.metrics.DeploymentMetricsCollector;
import com.autopilot.service.deployment.v5.runtime.execution.report.ExecutionReports;
import com.autopilot.service.deployment.v5.runtime.execution.snapshot.ExecutionSnapshot;
import com.autopilot.service.deployment.v5.runtime.execution.stall.DeploymentStallDetector;
import com.autopilot.service.deployment.v5.runtime.execution.stall.StallReport;
import com.autopilot.service.deployment.v5.runtime.execution.stream.ExecutionLogStreamEvent;
import com.autopilot.service.deployment.v5.runtime.execution.stream.LogStreamingEngine;
import com.autopilot.service.deployment.v5.runtime.execution.timeout.AdaptiveTimeoutManager;
import com.autopilot.service.deployment.v5.runtime.execution.timeline.DeploymentTimeline;
import com.autopilot.service.deployment.v5.runtime.execution.tracker.DeploymentProgressTracker;
import com.autopilot.service.deployment.v5.runtime.execution.tracker.DeploymentStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Universal Deployment Execution, Live Diagnostics & Progress Streaming Engine.
 * Responsibilities:
 * - Observes runtime execution without performing infrastructure logic.
 * - Coordinates real-time log streaming across SSM, Docker, Spring, Frontend, Cloud-Init.
 * - Tracks stage-by-stage deployment progress and remaining time.
 * - Records structured phase timeline events.
 * - Collects execution metrics.
 * - Performs progress-driven stall detection and adaptive timeout management.
 * - Aggregates complete execution snapshots and diagnostic reports.
 *
 * @since V5.2 — Milestone 5.2 Compliance
 */
@Service
@RequiredArgsConstructor
public class DeploymentExecutionEngine {

    private final LogStreamingEngine logStreamingEngine;
    private final DeploymentProgressTracker progressTracker;
    private final DeploymentTimeline timeline;
    private final DeploymentMetricsCollector metricsCollector;
    private final DockerPullAnalyzer dockerPullAnalyzer;
    private final DeploymentStallDetector stallDetector;
    private final AdaptiveTimeoutManager timeoutManager;
    private final ExecutionDiagnosticsEngine diagnosticsEngine;

    private final Map<String, List<ExecutionEvent>> sessionEvents = new ConcurrentHashMap<>();

    public void startSession(String sessionId) {
        if (sessionId == null) return;
        metricsCollector.startSession(sessionId);
        progressTracker.updateProgress(sessionId, DeploymentStage.CLONE, 5, "Initializing Deployment Session");
        timeline.startPhase(sessionId, "CLONE");
        stallDetector.recordActivity(sessionId, "CLONE");
    }

    public void recordLog(String sessionId, ExecutionLogStreamEvent.LogSource source, String message, String level) {
        if (sessionId == null) return;
        logStreamingEngine.publishLog(sessionId, source, message, level);
        stallDetector.recordActivity(sessionId, null);
        progressTracker.recordActivity(sessionId);
    }

    public void publishEvent(String sessionId, ExecutionEvent event) {
        if (sessionId == null || event == null) return;
        sessionEvents.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(event);
        stallDetector.recordActivity(sessionId, event.getStage());
        progressTracker.recordActivity(sessionId);
        if (event.getStage() != null && event.getMessage() != null) {
            timeline.addEvent(sessionId, event.getStage(), event.getMessage());
        }
    }

    public void advanceStage(String sessionId, DeploymentStage stage, int percentage, String currentOperation) {
        if (sessionId == null) return;
        progressTracker.updateProgress(sessionId, stage, percentage, currentOperation);
        timeline.startPhase(sessionId, stage.name());
        stallDetector.recordActivity(sessionId, stage.name());
    }

    public void recordMetric(String sessionId, String metricKey, long durationMs) {
        metricsCollector.recordMetric(sessionId, metricKey, durationMs);
    }

    public StallReport evaluateStallStatus(String sessionId) {
        return stallDetector.checkStall(sessionId);
    }

    public long calculateStageTimeoutMs(String stage, Map<String, Object> context) {
        return timeoutManager.calculateTimeoutMs(stage, context);
    }

    public ImageOptimizationReport analyzeImageOptimization(String imageName, long sizeBytes) {
        return dockerPullAnalyzer.analyzeImage(imageName, sizeBytes);
    }

    public ExecutionReports.DeploymentExecutionReport generateReport(String sessionId, boolean success, String rawError) {
        var progressSnap = progressTracker.getSnapshot(sessionId);
        var timelinePhases = timeline.getTimelineForSession(sessionId);
        var metricsSnap = metricsCollector.getSnapshot(sessionId);
        var logsList = logStreamingEngine.getLogsForSession(sessionId);

        String snippet = logsList.stream()
                .filter(l -> "ERROR".equalsIgnoreCase(l.getStreamLevel()) || "WARN".equalsIgnoreCase(l.getStreamLevel()))
                .reduce((first, second) -> second)
                .map(ExecutionLogStreamEvent::getMessage)
                .orElse(rawError != null ? rawError : "No error snippet available");

        var failureReport = !success ? diagnosticsEngine.classifyFailure(sessionId, progressSnap.getStage().name(), rawError, snippet) : null;
        var imageReport = dockerPullAnalyzer.analyzeImage("backend-service-image", 1_900_000_000L);

        var progressReport = ExecutionReports.DeploymentProgressReport.builder().sessionId(sessionId).progress(progressSnap).build();
        var timelineReport = ExecutionReports.DeploymentTimelineReport.builder().sessionId(sessionId).phases(timelinePhases).build();
        var metricsReport = ExecutionReports.DeploymentMetricsReport.builder().sessionId(sessionId).metrics(metricsSnap).build();
        var diagReport = ExecutionReports.DeploymentDiagnosticsReport.builder().sessionId(sessionId).healthy(success).failureReport(failureReport).imageOptimizationReport(imageReport).build();
        var logReport = ExecutionReports.DeploymentLogReport.builder().sessionId(sessionId).totalLogLines(logsList.size()).logs(logsList).build();

        return ExecutionReports.DeploymentExecutionReport.builder()
                .sessionId(sessionId)
                .success(success)
                .stage(progressSnap.getStage().name())
                .progressReport(progressReport)
                .timelineReport(timelineReport)
                .metricsReport(metricsReport)
                .diagnosticsReport(diagReport)
                .logReport(logReport)
                .build();
    }

    public ExecutionSnapshot takeSnapshot(String sessionId, boolean success, String rawError) {
        return ExecutionSnapshot.builder()
                .sessionId(sessionId)
                .timestamp(System.currentTimeMillis())
                .executionReport(generateReport(sessionId, success, rawError))
                .build();
    }
}
