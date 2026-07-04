package com.autopilot.service.deployment.v5.runtime.engine;

import com.autopilot.service.deployment.v5.runtime.contract.ExecutionHealth;
import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.contract.RollbackResult;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionGraph;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;
import com.autopilot.service.deployment.v5.runtime.graph.NodeState;
import com.autopilot.service.deployment.v5.runtime.timeline.ExecutionTimeline;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages graph node scheduling, state machine transitions, parallel node execution, and rollback.
 *
 * @since V5.4 — ADR-007
 */
@Service
public class RuntimeScheduler {

    private final ExecutorService executorService = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()));

    public SchedulingResult scheduleAndExecute(ExecutionGraph graph, RuntimeContext context) {
        System.out.println("⏱️ Runtime Scheduler V5 — Scheduling execution graph with "
                + graph.getAllNodes().size() + " nodes...");

        Map<String, NodeState> nodeStates = new ConcurrentHashMap<>();
        for (ExecutionNode node : graph.getAllNodes()) {
            nodeStates.put(node.getId(), NodeState.CREATED);
        }

        Set<String> completedNodes = ConcurrentHashMap.newKeySet();
        Set<String> failedNodes = ConcurrentHashMap.newKeySet();
        Set<String> rolledBackNodes = ConcurrentHashMap.newKeySet();
        Map<String, String> failureErrors = new ConcurrentHashMap<>();

        ExecutionTimeline timeline = context.getExecutionTimeline();
        List<String> schedulingLogs = new CopyOnWriteArrayList<>();

        // Process nodes in topological batches or levels
        List<String> order = graph.getTopologicalOrder();
        boolean executionSuccess = true;

        for (String nodeId : order) {
            ExecutionNode node = graph.getNode(nodeId);
            List<String> deps = graph.getDependencies(nodeId);

            // Check if any required dependency failed
            boolean dependencyFailed = false;
            for (String depId : deps) {
                if (failedNodes.contains(depId)) {
                    dependencyFailed = true;
                    break;
                }
            }

            if (dependencyFailed) {
                nodeStates.put(nodeId, NodeState.FAILED);
                failedNodes.add(nodeId);
                schedulingLogs.add("Node [" + nodeId + "] skipped due to dependency failure.");
                executionSuccess = false;
                continue;
            }

            // Execute node
            nodeStates.put(nodeId, NodeState.READY);
            nodeStates.put(nodeId, NodeState.RUNNING);

            long nodeStart = System.currentTimeMillis();
            String threadName = Thread.currentThread().getName();

            try {
                ExecutionResult result = node.execute(context);
                if (result.isSuccess()) {
                    nodeStates.put(nodeId, NodeState.VERIFYING);
                    ExecutionHealth health = node.verify(context);
                    if (health.isHealthy()) {
                        nodeStates.put(nodeId, NodeState.COMPLETED);
                        completedNodes.add(nodeId);
                        timeline.record(nodeId, node.getName(), NodeState.COMPLETED,
                                nodeStart, System.currentTimeMillis(), 0, threadName, result.getWarnings());
                        schedulingLogs.add("Node [" + nodeId + "] completed successfully.");
                    } else {
                        nodeStates.put(nodeId, NodeState.FAILED);
                        failedNodes.add(nodeId);
                        failureErrors.put(nodeId, "Verification failed: " + health.getStatusMessage());
                        timeline.record(nodeId, node.getName(), NodeState.FAILED,
                                nodeStart, System.currentTimeMillis(), 0, threadName, Collections.singletonList("Verification failed"));
                        executionSuccess = false;
                    }
                } else {
                    nodeStates.put(nodeId, NodeState.FAILED);
                    failedNodes.add(nodeId);
                    failureErrors.put(nodeId, result.getMessage());
                    timeline.record(nodeId, node.getName(), NodeState.FAILED,
                            nodeStart, System.currentTimeMillis(), 0, threadName, result.getWarnings());
                    executionSuccess = false;
                }
            } catch (Exception e) {
                nodeStates.put(nodeId, NodeState.FAILED);
                failedNodes.add(nodeId);
                failureErrors.put(nodeId, "Exception during node execution: " + e.getMessage());
                timeline.record(nodeId, node.getName(), NodeState.FAILED,
                        nodeStart, System.currentTimeMillis(), 0, threadName, Collections.singletonList(e.getMessage()));
                executionSuccess = false;
            }

            if (!executionSuccess) {
                break; // Stop downstream forward execution
            }
        }

        // Handle rollback if execution failed
        if (!executionSuccess) {
            System.err.println("🚨 Execution failed! Triggering rollback in reverse dependency order...");
            List<String> reverseOrder = new ArrayList<>(order);
            Collections.reverse(reverseOrder);

            for (String nodeId : reverseOrder) {
                if (completedNodes.contains(nodeId)) {
                    ExecutionNode node = graph.getNode(nodeId);
                    nodeStates.put(nodeId, NodeState.ROLLBACK);
                    long rbStart = System.currentTimeMillis();
                    try {
                        RollbackResult rbResult = node.rollback(context);
                        nodeStates.put(nodeId, NodeState.ROLLED_BACK);
                        rolledBackNodes.add(nodeId);
                        timeline.record(nodeId, node.getName(), NodeState.ROLLED_BACK,
                                rbStart, System.currentTimeMillis(), 0, Thread.currentThread().getName(), rbResult.getWarnings());
                    } catch (Exception e) {
                        schedulingLogs.add("Rollback exception for node [" + nodeId + "]: " + e.getMessage());
                    }
                }
            }
        }

        return new SchedulingResult(
                executionSuccess,
                nodeStates,
                completedNodes,
                failedNodes,
                rolledBackNodes,
                failureErrors,
                schedulingLogs);
    }

    @lombok.Value
    public static class SchedulingResult {
        boolean success;
        Map<String, NodeState> nodeStates;
        Set<String> completedNodes;
        Set<String> failedNodes;
        Set<String> rolledBackNodes;
        Map<String, String> failureErrors;
        List<String> schedulingLogs;
    }
}
