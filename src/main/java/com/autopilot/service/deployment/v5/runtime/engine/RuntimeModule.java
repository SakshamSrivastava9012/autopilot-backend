package com.autopilot.service.deployment.v5.runtime.engine;

import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;

/**
 * Interface for deployment capabilities (Infrastructure, Dependency, Credential, Container, etc.).
 * Modules are auto-discovered by RuntimeModuleRegistry via Spring DI.
 *
 * @since V5.4 — ADR-007
 */
public interface RuntimeModule {

    String id();

    boolean supports(RuntimeContext context);

    ExecutionNode createNode(RuntimeContext context);
}
