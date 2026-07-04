package com.autopilot.service.deployment.v5.runtime.graph;

import com.autopilot.service.deployment.v5.runtime.report.GraphValidationReport;

import java.util.*;

/**
 * Builds and validates an ExecutionGraph from a collection of ExecutionNodes.
 * Performs cycle detection, topological sorting, and parallel candidate identification.
 *
 * @since V5.4 — ADR-007
 */
public class ExecutionGraphBuilder {

    /**
     * Builds and validates an ExecutionGraph.
     * Throws IllegalStateException if a dependency cycle is detected.
     */
    public ExecutionGraph build(List<ExecutionNode> rawNodes) {
        Map<String, ExecutionNode> nodeMap = new LinkedHashMap<>();
        for (ExecutionNode node : rawNodes) {
            nodeMap.put(node.getId(), node);
        }

        Map<String, List<String>> adjacencyList = new LinkedHashMap<>();        // u -> v (u must run before v)
        Map<String, List<String>> reverseAdjacencyList = new LinkedHashMap<>(); // v -> u (v depends on u)
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (String id : nodeMap.keySet()) {
            adjacencyList.put(id, new ArrayList<>());
            reverseAdjacencyList.put(id, new ArrayList<>());
            inDegree.put(id, 0);
        }

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Build edges
        for (ExecutionNode node : nodeMap.values()) {
            String targetId = node.getId();
            List<String> dependencies = node.dependsOn();
            if (dependencies != null) {
                for (String depId : dependencies) {
                    if (!nodeMap.containsKey(depId)) {
                        warnings.add("Node '" + targetId + "' depends on unknown node '" + depId + "'. Dependency ignored.");
                        continue;
                    }
                    adjacencyList.get(depId).add(targetId);
                    reverseAdjacencyList.get(targetId).add(depId);
                    inDegree.put(targetId, inDegree.get(targetId) + 1);
                }
            }
        }

        // Kahn's algorithm for topological sorting and cycle detection
        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> topologicalOrder = new ArrayList<>();
        List<String> parallelCandidates = new ArrayList<>();

        while (!queue.isEmpty()) {
            if (queue.size() > 1) {
                parallelCandidates.add("Parallel execution group: " + queue);
            }
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                String u = queue.poll();
                topologicalOrder.add(u);
                for (String v : adjacencyList.get(u)) {
                    inDegree.put(v, inDegree.get(v) - 1);
                    if (inDegree.get(v) == 0) {
                        queue.add(v);
                    }
                }
            }
        }

        boolean hasCycle = topologicalOrder.size() != nodeMap.size();
        if (hasCycle) {
            Set<String> unvisited = new HashSet<>(nodeMap.keySet());
            unvisited.removeAll(topologicalOrder);
            errors.add("Dependency cycle detected involving nodes: " + unvisited);
        }

        int totalEdges = 0;
        for (List<String> edges : adjacencyList.values()) {
            totalEdges += edges.size();
        }

        GraphValidationReport report = GraphValidationReport.builder()
                .valid(!hasCycle && errors.isEmpty())
                .nodeCount(nodeMap.size())
                .edgeCount(totalEdges)
                .topologicalOrder(topologicalOrder)
                .parallelCandidates(parallelCandidates)
                .errors(errors)
                .warnings(warnings)
                .build();

        if (hasCycle) {
            throw new IllegalStateException("Execution graph construction failed due to dependency cycles: " + errors);
        }

        return new ExecutionGraph(nodeMap, adjacencyList, reverseAdjacencyList, topologicalOrder, report);
    }
}
