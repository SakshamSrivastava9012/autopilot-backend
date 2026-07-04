package com.autopilot.service.deployment.v5.runtime.graph;

import com.autopilot.service.deployment.v5.runtime.contract.ExecutionHealth;
import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.contract.RollbackResult;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;

import java.util.List;

/**
 * Interface representing a node in the deployment execution graph.
 * Nodes must be stateless.
 *
 * @since V5.4 — ADR-007
 */
public interface ExecutionNode {

    String getId();

    String getName();

    ExecutionPhase phase();

    List<String> dependsOn();

    List<String> provides();

    List<String> requires();

    ExecutionResult execute(RuntimeContext context);

    RollbackResult rollback(RuntimeContext context);

    ExecutionHealth verify(RuntimeContext context);
}
