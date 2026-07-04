package com.autopilot.service.deployment.v5.runtime.execution;

import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.DockerPullAnalyzer;
import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.ExecutionDiagnosticsEngine;
import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.DeploymentFailureReport;
import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.ImageOptimizationReport;
import com.autopilot.service.deployment.v5.runtime.execution.engine.DeploymentExecutionEngine;
import com.autopilot.service.deployment.v5.runtime.execution.events.ExecutionEvents;
import com.autopilot.service.deployment.v5.runtime.execution.metrics.DeploymentMetricsCollector;
import com.autopilot.service.deployment.v5.runtime.execution.stall.DeploymentStallDetector;
import com.autopilot.service.deployment.v5.runtime.execution.stall.StallReport;
import com.autopilot.service.deployment.v5.runtime.execution.stream.ExecutionLogStreamEvent;
import com.autopilot.service.deployment.v5.runtime.execution.stream.LogStreamingEngine;
import com.autopilot.service.deployment.v5.runtime.execution.timeout.AdaptiveTimeoutManager;
import com.autopilot.service.deployment.v5.runtime.execution.timeline.DeploymentTimeline;
import com.autopilot.service.deployment.v5.runtime.execution.tracker.DeploymentProgressTracker;
import com.autopilot.service.deployment.v5.runtime.execution.tracker.DeploymentStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Deployrix V5 — Milestone 5.2 Diagnostics & Execution Test Suite")
public class DeploymentExecutionDiagnosticsTest {

    private LogStreamingEngine logStreamingEngine;
    private DeploymentProgressTracker progressTracker;
    private DeploymentTimeline timeline;
    private DeploymentMetricsCollector metricsCollector;
    private DockerPullAnalyzer dockerPullAnalyzer;
    private DeploymentStallDetector stallDetector;
    private AdaptiveTimeoutManager timeoutManager;
    private ExecutionDiagnosticsEngine diagnosticsEngine;
    private DeploymentExecutionEngine executionEngine;

    @BeforeEach
    void setUp() {
        logStreamingEngine = new LogStreamingEngine();
        progressTracker = new DeploymentProgressTracker();
        timeline = new DeploymentTimeline();
        metricsCollector = new DeploymentMetricsCollector();
        dockerPullAnalyzer = new DockerPullAnalyzer();
        stallDetector = new DeploymentStallDetector();
        timeoutManager = new AdaptiveTimeoutManager();
        diagnosticsEngine = new ExecutionDiagnosticsEngine();

        executionEngine = new DeploymentExecutionEngine(
                logStreamingEngine,
                progressTracker,
                timeline,
                metricsCollector,
                dockerPullAnalyzer,
                stallDetector,
                timeoutManager,
                diagnosticsEngine
        );
    }

    @Test
    @DisplayName("Verify real-time log streaming across SSM, Docker, and Spring sources")
    void testLogStreamingEngine() {
        String sessionId = "sess-log-test";

        logStreamingEngine.publishLog(sessionId, ExecutionLogStreamEvent.LogSource.SSM_STDOUT, "Pulling image...", "INFO");
        logStreamingEngine.publishLog(sessionId, ExecutionLogStreamEvent.LogSource.DOCKER, "Digest: sha256:12345", "INFO");
        logStreamingEngine.publishLog(sessionId, ExecutionLogStreamEvent.LogSource.SPRING_BOOT, "Started AutopilotBackendApplication in 4.2 seconds", "INFO");

        var logs = logStreamingEngine.getLogsForSession(sessionId);
        assertEquals(3, logs.size());
        assertEquals(ExecutionLogStreamEvent.LogSource.SSM_STDOUT, logs.get(0).getSource());
        assertEquals(ExecutionLogStreamEvent.LogSource.DOCKER, logs.get(1).getSource());
        assertEquals(ExecutionLogStreamEvent.LogSource.SPRING_BOOT, logs.get(2).getSource());
    }

    @Test
    @DisplayName("Verify progress tracker stages, percentages, and operations")
    void testProgressTracker() {
        String sessionId = "sess-prog-test";

        progressTracker.updateProgress(sessionId, DeploymentStage.CLONE, 5, "Cloning main branch");
        var snap1 = progressTracker.getSnapshot(sessionId);
        assertEquals(DeploymentStage.CLONE, snap1.getStage());
        assertEquals(5, snap1.getPercentage());

        progressTracker.updateProgress(sessionId, DeploymentStage.DOCKER_PULL, 50, "Pulling 18/25 layers");
        var snap2 = progressTracker.getSnapshot(sessionId);
        assertEquals(DeploymentStage.DOCKER_PULL, snap2.getStage());
        assertEquals(50, snap2.getPercentage());
        assertEquals("Pulling 18/25 layers", snap2.getCurrentOperation());
    }

    @Test
    @DisplayName("Verify timeline phase recording and warning additions")
    void testDeploymentTimeline() {
        String sessionId = "sess-timeline-test";

        timeline.startPhase(sessionId, "PROVISIONING");
        timeline.addEvent(sessionId, "PROVISIONING", "RDS Provisioned");
        timeline.addWarning(sessionId, "PROVISIONING", "DB instance started in default subnet");
        timeline.completePhase(sessionId, "PROVISIONING", true);

        var phases = timeline.getTimelineForSession(sessionId);
        assertEquals(1, phases.size());
        assertEquals("PROVISIONING", phases.get(0).getPhaseName());
        assertEquals("COMPLETED", phases.get(0).getStatus());
        assertEquals(1, phases.get(0).getEvents().size());
        assertEquals(1, phases.get(0).getWarnings().size());
    }

    @Test
    @DisplayName("Verify adaptive stall detection (active progress vs stall threshold)")
    void testStallDetector() {
        String sessionId = "sess-stall-test";

        stallDetector.recordActivity(sessionId, "DOCKER_PULL");
        StallReport snapActive = stallDetector.checkStall(sessionId, 5000);
        assertFalse(snapActive.isStalled(), "Active progress should not be marked as stalled");

        // Test stall with zero threshold
        StallReport snapStalled = stallDetector.checkStall(sessionId, -1);
        assertTrue(snapStalled.isStalled(), "Zero duration threshold with past activity should flag stall");
    }

    @Test
    @DisplayName("Verify adaptive context-aware dynamic timeouts")
    void testAdaptiveTimeoutManager() {
        // Test Docker Pull for large image (>1.5GB) -> 15 min (900,000 ms)
        Map<String, Object> dockerCtx = new HashMap<>();
        dockerCtx.put("imageSizeBytes", 2_000_000_000L);
        long dockerTimeout = timeoutManager.calculateTimeoutMs("DOCKER_PULL", dockerCtx);
        assertEquals(15 * 60 * 1000L, dockerTimeout);

        // Test Spring Boot with Flyway migrations -> 180 sec (180,000 ms)
        Map<String, Object> springCtx = new HashMap<>();
        springCtx.put("hasMigrations", true);
        long springTimeout = timeoutManager.calculateTimeoutMs("SPRING_BOOT_STARTUP", springCtx);
        assertEquals(180 * 1000L, springTimeout);

        // Test Next.js build -> 300 sec (300,000 ms)
        Map<String, Object> nextCtx = new HashMap<>();
        nextCtx.put("framework", "NEXT_JS");
        long nextTimeout = timeoutManager.calculateTimeoutMs("IMAGE_BUILD", nextCtx);
        assertEquals(300 * 1000L, nextTimeout);
    }

    @Test
    @DisplayName("Verify failure diagnostics classification")
    void testExecutionDiagnosticsEngine() {
        String sessionId = "sess-diag-test";

        DeploymentFailureReport oomReport = diagnosticsEngine.classifyFailure(sessionId, "CONTAINER_STARTUP", "Command killed with exit status 137 OOM", "");
        assertEquals(DeploymentFailureReport.FailureCategory.OOMKilled, oomReport.getFailureCategory());

        DeploymentFailureReport springReport = diagnosticsEngine.classifyFailure(sessionId, "HEALTH", "BeanCreationException in ApplicationContext", "");
        assertEquals(DeploymentFailureReport.FailureCategory.SpringBootFailed, springReport.getFailureCategory());

        DeploymentFailureReport ssmReport = diagnosticsEngine.classifyFailure(sessionId, "PROVISIONING", "SSM Agent Connection Disconnected", "");
        assertEquals(DeploymentFailureReport.FailureCategory.SSMDisconnected, ssmReport.getFailureCategory());
    }

    @Test
    @DisplayName("Verify Docker image pull optimization analyzer recommendations")
    void testDockerPullAnalyzer() {
        ImageOptimizationReport report = dockerPullAnalyzer.analyzeImage("backend-service-image", 2_100_000_000L); // 2.1 GB

        assertNotNull(report);
        assertEquals("backend-service-image", report.getImageName());
        assertTrue(report.getPotentialSavingsBytes() > 0);
        assertTrue(report.getSavingsPercentage() > 50.0);
        assertFalse(report.getRecommendations().isEmpty());
    }

    @Test
    @DisplayName("Verify full DeploymentExecutionEngine orchestration snapshot")
    void testDeploymentExecutionEngineSnapshot() {
        String sessionId = "sess-engine-test";

        executionEngine.startSession(sessionId);
        executionEngine.recordLog(sessionId, ExecutionLogStreamEvent.LogSource.SSM_STDOUT, "Started clone", "INFO");
        executionEngine.publishEvent(sessionId, ExecutionEvents.DeploymentStartedEvent.builder()
                .sessionId(sessionId)
                .stage("CLONE")
                .message("Clone started")
                .projectName("ai-prompt-vault-project")
                .deploymentMode("MANAGED")
                .build());

        executionEngine.advanceStage(sessionId, DeploymentStage.DOCKER_PULL, 50, "Pulling layers");
        executionEngine.recordMetric(sessionId, "CLONE", 1200L);

        var snapshot = executionEngine.takeSnapshot(sessionId, true, null);
        assertNotNull(snapshot);
        assertEquals(sessionId, snapshot.getSessionId());
        assertTrue(snapshot.getExecutionReport().isSuccess());
        assertNotNull(snapshot.getExecutionReport().getProgressReport());
        assertNotNull(snapshot.getExecutionReport().getMetricsReport());
        assertNotNull(snapshot.getExecutionReport().getDiagnosticsReport());
    }
}
