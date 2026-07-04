package com.autopilot.service.deployment.v5.runtime.adapter;

import com.autopilot.service.deployment.v5.runtime.contract.ExecutionHealth;
import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.contract.RollbackResult;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionPhase;

import java.util.Collections;
import java.util.List;

/**
 * Base implementation for stateless runtime execution nodes.
 *
 * @since V5.4 — ADR-007
 */
public abstract class AbstractRuntimeNode implements ExecutionNode {

    private final String id;
    private final String name;
    private final ExecutionPhase phase;
    private final List<String> dependsOn;

    protected AbstractRuntimeNode(String id, String name, ExecutionPhase phase, List<String> dependsOn) {
        this.id = id;
        this.name = name;
        this.phase = phase;
        this.dependsOn = dependsOn != null ? Collections.unmodifiableList(dependsOn) : Collections.emptyList();
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public ExecutionPhase phase() { return phase; }
    @Override public List<String> dependsOn() { return dependsOn; }
    @Override public List<String> provides() { return Collections.singletonList(id); }
    @Override public List<String> requires() { return dependsOn; }

    @Override
    public RollbackResult rollback(RuntimeContext context) {
        System.out.println("🔄 Rolling back node: [" + getId() + "] " + getName());
        return RollbackResult.builder()
                .success(true)
                .nodeId(getId())
                .message("Rollback completed for " + getName())
                .logs(Collections.singletonList("Default rollback executed for " + getId()))
                .warnings(Collections.emptyList())
                .durationMs(5)
                .build();
    }

    @Override
    public ExecutionHealth verify(RuntimeContext context) {
        return ExecutionHealth.builder()
                .healthy(true)
                .nodeId(getId())
                .statusMessage("Node " + getName() + " healthy")
                .diagnostics(Collections.emptyList())
                .build();
    }
}
