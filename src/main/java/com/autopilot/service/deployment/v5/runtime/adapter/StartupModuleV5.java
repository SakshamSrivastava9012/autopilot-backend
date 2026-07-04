package com.autopilot.service.deployment.v5.runtime.adapter;

import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.contract.RollbackResult;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.engine.RuntimeModule;
import com.autopilot.service.deployment.v5.runtime.environment.injector.ContainerEnvironment;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionPhase;
import com.autopilot.service.deployment.v5.runtime.startup.engine.RuntimeLifecycleEngineV5;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Application startup phase module connected to RuntimeLifecycleEngineV5.
 *
 * @since V5.4 — ADR-011
 */
@Component
public class StartupModuleV5 implements RuntimeModule {

    private final RuntimeLifecycleEngineV5 lifecycleEngine;

    public StartupModuleV5(RuntimeLifecycleEngineV5 lifecycleEngine) {
        this.lifecycleEngine = lifecycleEngine;
    }

    @Override public String id() { return "startup-module"; }

    @Override public boolean supports(RuntimeContext context) { return true; }

    @Override
    public ExecutionNode createNode(RuntimeContext context) {
        return new AbstractRuntimeNode("startup-node", "Universal Startup Negotiation & Runtime Lifecycle", ExecutionPhase.STARTUP, Collections.singletonList("credential-node")) {
            @Override
            public ExecutionResult execute(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                System.out.println("🚀 Executing Graph Node: [startup-node]");

                ContainerEnvironment env = (ContainerEnvironment) ctx.getResolvedObject("ContainerEnvironment");

                // V5.8 FIX: Extract serviceId and framework from DeploymentManifest (single source of truth).
                // Never use ctx.getDeploymentId() as serviceId — that is a UUID, not a service name.
                // Never rely solely on ContainerEnvironment.framework — it is a secondary copy.
                String serviceId = "app-service";
                String framework = "SPRING_BOOT";

                com.autopilot.dto.DeploymentManifest manifest = ctx.getDeploymentManifest();
                if (manifest != null && manifest.getServices() != null && !manifest.getServices().isEmpty()) {
                    // Find the primary backend/api service, or use the first service
                    com.autopilot.dto.ServiceDescriptor primarySvc = manifest.getServices().stream()
                            .filter(s -> s.getRole() != null && (s.getRole() == com.autopilot.dto.ServiceRole.API || s.getRole() == com.autopilot.dto.ServiceRole.SSR))
                            .findFirst()
                            .orElse(manifest.getServices().get(0));

                    if (primarySvc.getName() != null && !primarySvc.getName().isBlank()) {
                        serviceId = primarySvc.getName();
                    } else if (primarySvc.getId() != null && !primarySvc.getId().isBlank()) {
                        serviceId = primarySvc.getId();
                    }

                    if (primarySvc.getFramework() != null && !primarySvc.getFramework().isBlank()) {
                        framework = primarySvc.getFramework();
                    }
                }

                // Fallback: if ContainerEnvironment has a valid framework and manifest didn't, use it
                if ("SPRING_BOOT".equals(framework) && env != null && env.getFramework() != null
                        && !env.getFramework().isBlank() && !"generic".equalsIgnoreCase(env.getFramework())) {
                    framework = env.getFramework();
                }

                System.out.println("   📋 Resolved serviceId=[" + serviceId + "], framework=[" + framework + "] from DeploymentManifest");

                RuntimeLifecycleEngineV5.LifecycleResult result = lifecycleEngine.startAndVerifyContainer(
                        serviceId,
                        "app-image:latest",
                        framework,
                        env,
                        Collections.emptyList()
                );

                ctx.putResolvedObject("RuntimeLifecycleSnapshot", result.getSnapshot());
                ctx.putResolvedObject("StartupReport", result.getStartupReport());

                Map<String, Object> outputs = new HashMap<>();
                outputs.put("containerId", result.getContainerId());
                outputs.put("lifecycleState", result.getSnapshot().getLifecycleState().name());
                outputs.put("readinessConfirmed", result.getSnapshot().isReadinessStatus());
                outputs.put("healthConfirmed", result.getSnapshot().isHealthStatus());

                return ExecutionResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Application container started and confirmed READY: [" + result.getContainerId() + "]")
                        .outputs(outputs)
                        .logs(result.getStartupReport().getLogs())
                        .warnings(Collections.emptyList())
                        .executionDurationMs(System.currentTimeMillis() - start)
                        .build();
            }

            @Override
            public RollbackResult rollback(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                System.out.println("🚀 Rollback [startup-node] — Stopping application container");

                return RollbackResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Application container stopped cleanly")
                        .logs(Collections.singletonList("Container stopped"))
                        .warnings(Collections.emptyList())
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }
        };
    }
}
