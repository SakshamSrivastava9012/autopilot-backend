package com.autopilot.service.deployment.v5.runtime.graph;

import com.autopilot.service.deployment.v5.runtime.report.GraphValidationReport;

import java.util.*;

/**
 * Immutable execution graph representing dependency relationships between deployment steps.
 *
 * @since V5.4 — ADR-007
 */
public class ExecutionGraph {

    private final Map<String, ExecutionNode> nodes;
    private final Map<String, List<String>> adjacencyList; // nodeId -> list of dependent nodeIds
    private final Map<String, List<String>> reverseAdjacencyList; // nodeId -> list of nodeIds it depends on
    private final List<String> topologicalOrder;
    private final GraphValidationReport validationReport;

    public ExecutionGraph(Map<String, ExecutionNode> nodes,
                          Map<String, List<String>> adjacencyList,
                          Map<String, List<String>> reverseAdjacencyList,
                          List<String> topologicalOrder,
                          GraphValidationReport validationReport) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.adjacencyList = Collections.unmodifiableMap(new LinkedHashMap<>(adjacencyList));
        this.reverseAdjacencyList = Collections.unmodifiableMap(new LinkedHashMap<>(reverseAdjacencyList));
        this.topologicalOrder = Collections.unmodifiableList(new ArrayList<>(topologicalOrder));
        this.validationReport = validationReport;
    }

    public ExecutionNode getNode(String id) {
        return nodes.get(id);
    }

    public Collection<ExecutionNode> getAllNodes() {
        return nodes.values();
    }

    public List<String> getTopologicalOrder() {
        return topologicalOrder;
    }

    public List<String> getDependents(String nodeId) {
        return adjacencyList.getOrDefault(nodeId, Collections.emptyList());
    }

    public List<String> getDependencies(String nodeId) {
        return reverseAdjacencyList.getOrDefault(nodeId, Collections.emptyList());
    }

    public GraphValidationReport getValidationReport() {
        return validationReport;
    }
}
