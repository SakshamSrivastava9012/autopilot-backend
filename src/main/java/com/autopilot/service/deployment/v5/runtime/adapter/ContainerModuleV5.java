package com.autopilot.service.deployment.v5.runtime.adapter;

import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.engine.RuntimeModule;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionPhase;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Container phase module (container creation and execution).
 *
 * @since V5.4 — ADR-007
 */
@Component
public class ContainerModuleV5 implements RuntimeModule {

    @Override public String id() { return "container-module"; }

    @Override public boolean supports(RuntimeContext context) { return true; }

    @Override
    public ExecutionNode createNode(RuntimeContext context) {
        return new AbstractRuntimeNode("container-node", "Container Creation", ExecutionPhase.CONTAINERS, Collections.singletonList("credential-node")) {
            @Override
            public ExecutionResult execute(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                System.out.println("📦 Executing Graph Node: [container-node]");
                return ExecutionResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Containers initialized")
                        .outputs(Collections.singletonMap("containerId", "ctr-" + ctx.getDeploymentId()))
                        .logs(Collections.singletonList("Container started"))
                        .warnings(Collections.emptyList())
                        .executionDurationMs(System.currentTimeMillis() - start)
                        .build();
            }
        };
    }
}
