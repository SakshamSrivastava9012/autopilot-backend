package com.autopilot.service.deployment.v5.runtime.contract;

import com.autopilot.dto.DeploymentManifest;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionGraph;
import com.autopilot.service.deployment.v5.runtime.timeline.ExecutionTimeline;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable/Thread-safe context passed across execution nodes.
 * Modules and nodes communicate ONLY through RuntimeContext.
 *
 * @since V5.4 — ADR-007
 */
public class RuntimeContext {

    private final String deploymentId;
    private final DeploymentManifest deploymentManifest;
    private final Map<String, Object> runtimeContracts;
    private final Map<String, Object> infrastructureContracts;
    private final Map<String, Object> sharedReports;
    private final Map<String, Object> resolvedObjects = new ConcurrentHashMap<>();
    private ExecutionGraph executionGraph;
    private final ExecutionTimeline executionTimeline;

    public RuntimeContext(String deploymentId,
                          DeploymentManifest deploymentManifest,
                          Map<String, Object> runtimeContracts,
                          Map<String, Object> infrastructureContracts,
                          Map<String, Object> sharedReports,
                          ExecutionTimeline executionTimeline) {
        this.deploymentId = deploymentId;
        this.deploymentManifest = deploymentManifest;
        this.runtimeContracts = runtimeContracts != null ? Collections.unmodifiableMap(runtimeContracts) : Collections.emptyMap();
        this.infrastructureContracts = infrastructureContracts != null ? Collections.unmodifiableMap(infrastructureContracts) : Collections.emptyMap();
        this.sharedReports = sharedReports != null ? Collections.unmodifiableMap(sharedReports) : Collections.emptyMap();
        this.executionTimeline = executionTimeline != null ? executionTimeline : new ExecutionTimeline();
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public DeploymentManifest getDeploymentManifest() {
        return deploymentManifest;
    }

    public Map<String, Object> getRuntimeContracts() {
        return runtimeContracts;
    }

    public Map<String, Object> getInfrastructureContracts() {
        return infrastructureContracts;
    }

    public Map<String, Object> getSharedReports() {
        return sharedReports;
    }

    public ExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    public void setExecutionGraph(ExecutionGraph executionGraph) {
        this.executionGraph = executionGraph;
    }

    public ExecutionTimeline getExecutionTimeline() {
        return executionTimeline;
    }

    public Object getResolvedObject(String key) {
        return resolvedObjects.get(key);
    }

    public void putResolvedObject(String key, Object val) {
        if (key != null && val != null) {
            resolvedObjects.put(key, val);
        }
    }

    public Map<String, Object> getAllResolvedObjects() {
        return Collections.unmodifiableMap(resolvedObjects);
    }
}
