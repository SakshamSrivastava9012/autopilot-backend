package com.autopilot.service.deployment.v5.runtime.adapter;

import com.autopilot.service.deployment.v5.negotiation.DependencyContract;
import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.contract.RollbackResult;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.dependency.engine.DependencyProvisionEngineV5;
import com.autopilot.service.deployment.v5.runtime.engine.RuntimeModule;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionPhase;
import com.autopilot.service.deployment.v5.runtime.dependency.report.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Service dependency phase module connected to the DependencyProvisionEngineV5.
 * Supports multiple dependency instances keyed by unique dependency IDs (e.g., primary-db, session-cache).
 *
 * Enforces ADR-009/ADR-010 dynamic node dependencies:
 * - EXISTING_EXTERNAL: depends on dependency-validation-node (pre-flight validation before provision)
 * - DOCKER_RUNTIME / PLATFORM_MANAGED: depends on infrastructure-node (provision before post-validation)
 *
 * @since V5.4 — ADR-009
 */
@Component
public class DependencyModuleV5 implements RuntimeModule {

    private final DependencyProvisionEngineV5 dependencyEngine;

    public DependencyModuleV5(DependencyProvisionEngineV5 dependencyEngine) {
        this.dependencyEngine = dependencyEngine;
    }

    @Override public String id() { return "dependency-module"; }

    @Override public boolean supports(RuntimeContext context) { return true; }

    @Override
    public ExecutionNode createNode(RuntimeContext context) {
        String provider = resolveProviderFromContext(context);
        boolean isExternal = isExistingExternal(provider);
        List<String> dependsOn = isExternal
                ? Collections.singletonList("dependency-validation-node")
                : Collections.singletonList("infrastructure-node");

        return new AbstractRuntimeNode("dependency-node", "Dependency Provisioning Engine", ExecutionPhase.DEPENDENCIES, dependsOn) {
            @Override
            public ExecutionResult execute(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                System.out.println("🗄️ Executing Graph Node: [dependency-node] (Provider: " + provider + ")");

                DependencyContract contract = (DependencyContract) ctx.getResolvedObject("DependencyContract");
                if (contract == null) {
                    contract = DependencyContract.builder()
                            .dependencyId("primary-db")
                            .type("postgresql")
                            .provider(provider)
                            .version("16")
                            .ownership(OwnershipType.PLATFORM)
                            .build();
                }

                DependencyNegotiationReport negotiationReport = DependencyNegotiationReport.builder()
                        .dependencyId(contract.getDependencyId())
                        .dependencyType(contract.getType())
                        .negotiatedProvider(contract.getProvider())
                        .decisionReason("Universal provider selection mapping")
                        .rulesMatched(Collections.singletonList("RULE_UNIVERSAL_PROVIDER"))
                        .warnings(Collections.emptyList())
                        .build();
                ctx.putResolvedObject("DependencyNegotiationReport", negotiationReport);

                DependencyProvisionEngineV5.ProvisioningResult result = dependencyEngine.provision(contract);

                Map<String, RuntimeDependency> runtimeDeps = new LinkedHashMap<>();
                runtimeDeps.put(result.getDependencyId(), result.getRuntimeDependency());
                ctx.putResolvedObject("RuntimeDependencies", runtimeDeps);

                Map<String, ResolvedCredentialContract> resolvedCreds = new LinkedHashMap<>();
                resolvedCreds.put(result.getDependencyId(), result.getCredentials());
                ctx.putResolvedObject("ResolvedCredentialContracts", resolvedCreds);

                ProvisioningReport provisioningReport = ProvisioningReport.builder()
                        .dependencyId(result.getDependencyId())
                        .provider(result.getRuntimeDependency().getProvider())
                        .success(result.getProvisionReport().isSuccess())
                        .durationMs(result.getProvisionReport().getDurationMs())
                        .runtimeStatus(result.getRuntimeDependency().getRuntimeStatus().name())
                        .logs(result.getProvisionReport().getLogs())
                        .warnings(result.getProvisionReport().getWarnings())
                        .build();
                ctx.putResolvedObject("ProvisioningReport", provisioningReport);

                ctx.putResolvedObject("CredentialResolutionReport", result.getCredentialReport());
                ctx.putResolvedObject("DependencySnapshot", result.getSnapshot());

                Map<String, Object> outputs = new HashMap<>();
                outputs.put("dependencyCount", 1);
                outputs.put("primaryEndpoint", result.getRuntimeDependency().getRuntimeEndpoint());
                outputs.put("primaryCredentialRef", result.getCredentials().getSecretReference());

                return ExecutionResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Dependencies provisioned successfully: [" + result.getDependencyId() + "]")
                        .outputs(outputs)
                        .logs(result.getProvisionReport().getLogs())
                        .warnings(Collections.emptyList())
                        .executionDurationMs(System.currentTimeMillis() - start)
                        .build();
            }

            @Override
            @SuppressWarnings("unchecked")
            public RollbackResult rollback(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                Object depsObj = ctx.getResolvedObject("RuntimeDependencies");
                int destroyed = 0;
                int preserved = 0;
                List<String> logs = new ArrayList<>();

                if (depsObj instanceof Map) {
                    Map<String, RuntimeDependency> depsMap = (Map<String, RuntimeDependency>) depsObj;
                    for (RuntimeDependency dep : depsMap.values()) {
                        var report = dependencyEngine.destroy(dep);
                        destroyed += report.getResourcesDestroyed();
                        preserved += report.getResourcesPreserved();
                        logs.addAll(report.getLogs());
                    }
                }

                return RollbackResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Dependency rollback completed. Destroyed: " + destroyed + ", Preserved: " + preserved)
                        .logs(logs)
                        .warnings(Collections.emptyList())
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }
        };
    }

    private String resolveProviderFromContext(RuntimeContext context) {
        if (context != null) {
            Object providerObj = context.getResolvedObject("TargetProvider");
            if (providerObj instanceof String) return (String) providerObj;

            Object contractObj = context.getResolvedObject("DependencyContract");
            if (contractObj instanceof DependencyContract) {
                DependencyContract dc = (DependencyContract) contractObj;
                if (dc.getProvider() != null) return dc.getProvider();
            }
        }
        return "DOCKER_RUNTIME";
    }

    private boolean isExistingExternal(String provider) {
        if (provider == null) return false;
        String p = provider.toUpperCase();
        return p.contains("EXISTING_EXTERNAL") || p.contains("EXTERNAL") || p.contains("ATLAS") || p.contains("NEON");
    }
}
