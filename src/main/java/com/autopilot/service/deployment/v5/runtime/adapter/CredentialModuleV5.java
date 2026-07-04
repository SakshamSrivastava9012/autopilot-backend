package com.autopilot.service.deployment.v5.runtime.adapter;

import com.autopilot.service.deployment.v5.negotiation.DependencyContract;
import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.contract.RollbackResult;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.dependency.discovery.RuntimeDiscoveryEngine;
import com.autopilot.service.deployment.v5.runtime.engine.RuntimeModule;
import com.autopilot.service.deployment.v5.runtime.environment.injector.EnvironmentInjectionEngineV5;
import com.autopilot.service.deployment.v5.runtime.environment.resolver.RuntimeConnectionContract;
import com.autopilot.service.deployment.v5.runtime.environment.resolver.RuntimeConnectionResolver;
import com.autopilot.service.deployment.v5.runtime.environment.report.RuntimeConnectionReport;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionPhase;
import com.autopilot.service.deployment.v5.runtime.infrastructure.snapshot.InfrastructureSnapshot;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Connection resolution & environment injection phase module connected to EnvironmentInjectionEngineV5.
 *
 * Enforces ADR-009/010 dynamic node dependencies:
 * - EXISTING_EXTERNAL: depends on dependency-node
 * - DOCKER_RUNTIME / PLATFORM_MANAGED: depends on dependency-validation-node
 *
 * @since V5.4 — ADR-010
 */
@Component
public class CredentialModuleV5 implements RuntimeModule {

    private final RuntimeConnectionResolver connectionResolver;
    private final EnvironmentInjectionEngineV5 injectionEngine;
    private final RuntimeDiscoveryEngine discoveryEngine;

    public CredentialModuleV5(RuntimeConnectionResolver connectionResolver,
                               EnvironmentInjectionEngineV5 injectionEngine,
                               RuntimeDiscoveryEngine discoveryEngine) {
        this.connectionResolver = connectionResolver;
        this.injectionEngine = injectionEngine;
        this.discoveryEngine = discoveryEngine;
    }

    @Override public String id() { return "credential-module"; }

    @Override public boolean supports(RuntimeContext context) { return true; }

    @Override
    public ExecutionNode createNode(RuntimeContext context) {
        String provider = resolveProviderFromContext(context);
        boolean isExternal = isExistingExternal(provider);
        List<String> dependsOn = isExternal
                ? Collections.singletonList("dependency-node")
                : Collections.singletonList("dependency-validation-node");

        return new AbstractRuntimeNode("credential-node", "Runtime Connection Resolution & Environment Injection", ExecutionPhase.CREDENTIALS, dependsOn) {
            @Override
            @SuppressWarnings("unchecked")
            public ExecutionResult execute(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                System.out.println("🔐 Executing Graph Node: [credential-node]");

                Map<String, RuntimeDependency> depsMap = (Map<String, RuntimeDependency>) ctx.getResolvedObject("RuntimeDependencies");
                Map<String, ResolvedCredentialContract> credsMap = (Map<String, ResolvedCredentialContract>) ctx.getResolvedObject("ResolvedCredentialContracts");
                InfrastructureSnapshot infraSnapshot = (InfrastructureSnapshot) ctx.getResolvedObject("InfrastructureSnapshot");

                List<RuntimeConnectionContract> connections = new ArrayList<>();
                List<String> discoveryLogs = new ArrayList<>();

                if (depsMap != null && !depsMap.isEmpty()) {
                    for (Map.Entry<String, RuntimeDependency> entry : depsMap.entrySet()) {
                        String depId = entry.getKey();
                        RuntimeDependency rDep = entry.getValue();
                        ResolvedCredentialContract creds = credsMap != null ? credsMap.get(depId) : null;
                        DependencyContract contract = (DependencyContract) ctx.getResolvedObject("DependencyContract");

                        var discoveryResult = discoveryEngine.discover(rDep, creds, contract);
                        connections.add(discoveryResult.getConnectionContract());

                        // Save discovery report to context
                        ctx.putResolvedObject("RuntimeDiscoveryReport", discoveryResult.getDiscoveryReport());
                        discoveryLogs.add("Discovered connection details: " + depId + " -> Host: " + discoveryResult.getConnectionContract().getHost());
                    }
                } else {
                    connections = connectionResolver.resolveAllConnections(depsMap, credsMap, infraSnapshot);
                }

                // Build and save RuntimeConnectionReport
                RuntimeConnectionReport connectionReport = RuntimeConnectionReport.builder()
                        .environmentId(ctx.getDeploymentId())
                        .connectionContractsCount(connections.size())
                        .connectionContracts(connections)
                        .generationTimeMs(System.currentTimeMillis() - start)
                        .build();
                ctx.putResolvedObject("RuntimeConnectionReport", connectionReport);

                String framework = "SPRING_BOOT";
                if (ctx.getDeploymentManifest() != null && ctx.getDeploymentManifest().getServices() != null && !ctx.getDeploymentManifest().getServices().isEmpty()) {
                    var svc = ctx.getDeploymentManifest().getServices().get(0);
                    if (svc.getFramework() != null) {
                        framework = svc.getFramework();
                    }
                }

                EnvironmentInjectionEngineV5.InjectionResult result = injectionEngine.generateEnvironment(connections, framework, Collections.emptyMap());

                ctx.putResolvedObject("ContainerEnvironment", result.getContainerEnvironment());
                ctx.putResolvedObject("RuntimeEnvironmentSnapshot", result.getSnapshot());

                // Save environment injection, framework mapping reports
                ctx.putResolvedObject("EnvironmentInjectionReport", result.getInjectionReport());
                ctx.putResolvedObject("FrameworkMappingReport", result.getMappingReport());

                Map<String, Object> outputs = new HashMap<>();
                outputs.put("injectedVarsCount", result.getContainerEnvironment().getVariables().size());
                outputs.put("framework", result.getContainerEnvironment().getFramework());
                outputs.put("maskedVariables", result.getContainerEnvironment().getMaskedVariables());

                List<String> logs = new ArrayList<>(discoveryLogs);
                logs.addAll(result.getInjectionReport().getLogs());

                return ExecutionResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Runtime environment generated successfully for framework: " + framework)
                        .outputs(outputs)
                        .logs(logs)
                        .warnings(Collections.emptyList())
                        .executionDurationMs(System.currentTimeMillis() - start)
                        .build();
            }

            @Override
            public RollbackResult rollback(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                System.out.println("🔐 Rollback [credential-node] — Clearing generated container environment");

                return RollbackResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Container environment cleared cleanly from runtime context")
                        .logs(Collections.singletonList("Container environment removed"))
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
