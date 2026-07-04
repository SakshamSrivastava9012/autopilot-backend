package com.autopilot.service.deployment.v5.runtime.adapter;

import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.contract.RollbackResult;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.engine.RuntimeModule;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionPhase;
import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.InfrastructureContract;
import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.InfrastructureResourceType;
import com.autopilot.service.deployment.v5.runtime.infrastructure.engine.InfrastructureProvisionEngineV5;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Infrastructure phase module connected to the InfrastructureProvisionEngineV5.
 *
 * @since V5.4 — ADR-008
 */
@Component
public class InfrastructureModuleV5 implements RuntimeModule {

    private final InfrastructureProvisionEngineV5 provisionEngine;

    public InfrastructureModuleV5(InfrastructureProvisionEngineV5 provisionEngine) {
        this.provisionEngine = provisionEngine;
    }

    @Override public String id() { return "infrastructure-module"; }

    @Override public boolean supports(RuntimeContext context) { return true; }

    @Override
    public ExecutionNode createNode(RuntimeContext context) {
        return new AbstractRuntimeNode("infrastructure-node", "Infrastructure Provisioning Engine", ExecutionPhase.INFRASTRUCTURE, Collections.emptyList()) {
            @Override
            public ExecutionResult execute(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                System.out.println("🧱 Executing Graph Node: [infrastructure-node]");

                // Construct immutable contract from deployment manifest or context
                InfrastructureContract contract = InfrastructureContract.builder()
                        .id("infra-" + ctx.getDeploymentId())
                        .provider("docker") // Defaults to docker adapter, extensible per manifest
                        .resourceType(InfrastructureResourceType.NETWORK)
                        .configuration(Collections.emptyMap())
                        .dependencies(Collections.emptyList())
                        .runtimeHints(Collections.emptyList())
                        .metadata(Collections.singletonMap("deploymentId", ctx.getDeploymentId()))
                        .ownership(OwnershipType.PLATFORM)
                        .region("local")
                        .tags(Collections.emptyMap())
                        .build();

                InfrastructureProvisionEngineV5.ProvisioningResult result = provisionEngine.provision(contract);

                // Update RuntimeContext ONLY with RuntimeInfrastructure and InfrastructureSnapshot
                ctx.putResolvedObject("RuntimeInfrastructure", result.getRuntimeInfrastructure());
                ctx.putResolvedObject("InfrastructureSnapshot", result.getSnapshot());

                Map<String, Object> outputs = new HashMap<>();
                outputs.put("networkId", result.getRuntimeInfrastructure().getIdentifier());
                outputs.put("provider", result.getRuntimeInfrastructure().getProvider());
                outputs.put("status", result.getRuntimeInfrastructure().getStatus().name());

                return ExecutionResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Infrastructure provisioned successfully via " + result.getRuntimeInfrastructure().getProvider())
                        .outputs(outputs)
                        .logs(result.getProvisionReport().getLogs())
                        .warnings(Collections.emptyList())
                        .executionDurationMs(System.currentTimeMillis() - start)
                        .build();
            }

            @Override
            public RollbackResult rollback(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                Object infraObj = ctx.getResolvedObject("RuntimeInfrastructure");
                if (infraObj instanceof com.autopilot.service.deployment.v5.runtime.infrastructure.contract.RuntimeInfrastructure) {
                    var report = provisionEngine.rollback((com.autopilot.service.deployment.v5.runtime.infrastructure.contract.RuntimeInfrastructure) infraObj);
                    return RollbackResult.builder()
                            .success(report.isSuccess())
                            .nodeId(getId())
                            .message("Infrastructure rollback completed. Deleted: " + report.getResourcesDeleted() + ", Preserved: " + report.getResourcesPreserved())
                            .logs(report.getLogs())
                            .warnings(Collections.emptyList())
                            .durationMs(System.currentTimeMillis() - start)
                            .build();
                }

                return super.rollback(ctx);
            }
        };
    }
}
