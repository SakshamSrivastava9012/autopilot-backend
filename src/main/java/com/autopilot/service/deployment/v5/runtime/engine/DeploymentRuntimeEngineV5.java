package com.autopilot.service.deployment.v5.runtime.engine;

import com.autopilot.dto.DeploymentManifest;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeEvents;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeSnapshot;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionGraph;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionGraphBuilder;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;
import com.autopilot.service.deployment.v5.runtime.graph.NodeState;
import com.autopilot.service.deployment.v5.runtime.report.ExecutionReports;
import com.autopilot.service.deployment.v5.runtime.timeline.ExecutionTimeline;
import com.autopilot.service.deployment.v5.migration.session.DeploymentSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The Universal Deployment Runtime Engine.
 *
 * Graph-driven orchestrator responsible for executing deployment plans described by immutable contracts.
 * It NEVER inspects repositories, negotiates providers, scans filesystems, modifies contracts, or generates nginx directly.
 *
 * Feature flag gated by deployrix.runtime.engine=v5
 *
 * @since V5.4 — ADR-007
 */
@Service
public class DeploymentRuntimeEngineV5 {

    private final RuntimeModuleRegistry moduleRegistry;
    private final RuntimeScheduler scheduler;
    private final ExecutionGraphBuilder graphBuilder;

    @Value("${deployrix.runtime.engine:legacy}")
    private String engineMode;

    public DeploymentRuntimeEngineV5(RuntimeModuleRegistry moduleRegistry,
                                      RuntimeScheduler scheduler) {
        this.moduleRegistry = moduleRegistry;
        this.scheduler = scheduler;
        this.graphBuilder = new ExecutionGraphBuilder();
    }

    public boolean isV5Enabled() {
        return "v5".equalsIgnoreCase(engineMode);
    }

    /**
     * Execute a deployment plan described by the DeploymentManifest.
     */
    public RuntimeSnapshot execute(DeploymentManifest manifest) {
        String deploymentId = manifest.getDeploymentId() != null ? manifest.getDeploymentId() : UUID.randomUUID().toString();
        System.out.println("⚡ Deployment Runtime Engine V5 — Executing deployment [" + deploymentId + "] (Engine Mode: " + engineMode + ")");
        long startTime = System.currentTimeMillis();

        ExecutionTimeline timeline = new ExecutionTimeline();
        RuntimeContext context = new RuntimeContext(
                deploymentId,
                manifest,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                timeline);

        // 1. Discover modules & build execution nodes
        List<RuntimeModule> modules = moduleRegistry.getSupportedModules(context);
        List<ExecutionNode> nodes = modules.stream()
                .map(m -> m.createNode(context))
                .collect(Collectors.toList());

        // 2. Build Execution Graph & validate for cycles
        ExecutionGraph graph = graphBuilder.build(nodes);
        context.setExecutionGraph(graph);

        // 3. Emit ExecutionStarted event
        RuntimeEvents.ExecutionStarted startEvent = new RuntimeEvents.ExecutionStarted(
                deploymentId, startTime, graph.getAllNodes().size());
        System.out.println("   Event Emitted: " + startEvent.getClass().getSimpleName() + " (nodes=" + startEvent.getTotalNodes() + ")");

        // 4. Schedule & Execute Graph
        RuntimeScheduler.SchedulingResult result = scheduler.scheduleAndExecute(graph, context);

        long endTime = System.currentTimeMillis();

        // 5. Build Reports
        ExecutionReports.ExecutionReport execReport = ExecutionReports.ExecutionReport.builder()
                .deploymentId(deploymentId)
                .success(result.isSuccess())
                .totalDurationMs(endTime - startTime)
                .totalNodes(graph.getAllNodes().size())
                .completedNodes(result.getCompletedNodes().size())
                .failedNodes(result.getFailedNodes().size())
                .rolledBackNodes(result.getRolledBackNodes().size())
                .warnings(Collections.emptyList())
                .errors(new ArrayList<>(result.getFailureErrors().values()))
                .build();

        Map<String, Long> nodeDurations = new HashMap<>();
        for (var entry : timeline.getEntries()) {
            nodeDurations.put(entry.getNodeId(), entry.getDurationMs());
        }

        ExecutionReports.TimelineReport timelineReport = ExecutionReports.TimelineReport.builder()
                .deploymentId(deploymentId)
                .totalDurationMs(endTime - startTime)
                .totalEvents(timeline.getEntries().size())
                .nodeDurations(nodeDurations)
                .threadUsage(timeline.getEntries().stream().map(ExecutionTimeline.TimelineEntry::getThreadName).distinct().collect(Collectors.toList()))
                .build();

        Map<NodeState, Integer> stateCounts = new HashMap<>();
        for (NodeState s : result.getNodeStates().values()) {
            stateCounts.put(s, stateCounts.getOrDefault(s, 0) + 1);
        }

        ExecutionReports.RuntimeSchedulingReport schedulingReport = ExecutionReports.RuntimeSchedulingReport.builder()
                .deploymentId(deploymentId)
                .nodeCount(graph.getAllNodes().size())
                .nodeStateCounts(stateCounts)
                .parallelBatches(graph.getValidationReport().getParallelCandidates())
                .schedulingLogs(result.getSchedulingLogs())
                .build();

        // 6. Emit ExecutionFinished event
        RuntimeEvents.ExecutionFinished finishEvent = new RuntimeEvents.ExecutionFinished(
                deploymentId, result.isSuccess(), endTime - startTime, endTime);
        System.out.println("   Event Emitted: " + finishEvent.getClass().getSimpleName() + " (success=" + finishEvent.isSuccess() + ")");

        // 7. Build and store DeploymentSession
        List<String> timelineEntries = timeline.getEntries().stream()
                .map(e -> e.getStartTimeEpoch() + ": [" + e.getNodeId() + "] " + e.getNodeName() + " (" + e.getDurationMs() + "ms)")
                .collect(Collectors.toList());

        Map<String, Object> sessionReports = new LinkedHashMap<>();
        sessionReports.put("ExecutionReport", execReport);
        sessionReports.put("TimelineReport", timelineReport);
        sessionReports.put("RuntimeSchedulingReport", schedulingReport);

        for (Map.Entry<String, Object> entry : context.getAllResolvedObjects().entrySet()) {
            if (entry.getKey().endsWith("Report") || entry.getKey().toLowerCase().contains("report")) {
                sessionReports.put(entry.getKey(), entry.getValue());
            }
        }

        DeploymentSession session = DeploymentSession.builder()
                .deploymentId(deploymentId)
                .repositoryModel(null)
                .deploymentManifest(manifest)
                .buildArtifact(null)
                .infrastructureSnapshot((com.autopilot.service.deployment.v5.runtime.infrastructure.snapshot.InfrastructureSnapshot) context.getResolvedObject("InfrastructureSnapshot"))
                .dependencySnapshot((com.autopilot.service.deployment.v5.runtime.dependency.snapshot.DependencySnapshot) context.getResolvedObject("DependencySnapshot"))
                .environmentSnapshot((com.autopilot.service.deployment.v5.runtime.environment.snapshot.RuntimeEnvironmentSnapshot) context.getResolvedObject("RuntimeEnvironmentSnapshot"))
                .startupSnapshot((com.autopilot.service.deployment.v5.runtime.startup.snapshot.RuntimeLifecycleSnapshot) context.getResolvedObject("RuntimeLifecycleSnapshot"))
                .reverseProxySnapshot((com.autopilot.service.deployment.v5.runtime.proxy.snapshot.ReverseProxySnapshot) context.getResolvedObject("ReverseProxySnapshot"))
                .verificationSnapshot((com.autopilot.service.deployment.v5.runtime.verification.snapshot.VerificationSnapshot) context.getResolvedObject("VerificationSnapshot"))
                .deploymentQualityReport((com.autopilot.service.deployment.v5.runtime.verification.report.VerificationReports.DeploymentQualityReport) context.getResolvedObject("DeploymentQualityReport"))
                .timeline(timelineEntries)
                .reports(sessionReports)
                .build();
        context.putResolvedObject("DeploymentSession", session);

        // 8. Build RuntimeSnapshot
        return RuntimeSnapshot.builder()
                .deploymentId(deploymentId)
                .success(result.isSuccess())
                .executionGraph(graph)
                .executionTimeline(timeline)
                .completedNodes(new ArrayList<>(result.getCompletedNodes()))
                .runningNodes(Collections.emptyList())
                .failedNodes(new ArrayList<>(result.getFailedNodes()))
                .executionReport(execReport)
                .timelineReport(timelineReport)
                .schedulingReport(schedulingReport)
                .infrastructureReferences(Collections.emptyMap())
                .runtimeReferences(Collections.emptyMap())
                .build();
    }
}
