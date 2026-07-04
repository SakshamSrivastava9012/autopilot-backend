package com.autopilot.service.deployment;

import com.autopilot.dto.DeploymentManifest;
import com.autopilot.service.deployment.module.CompatibilityModule;
import com.autopilot.service.deployment.module.Operation;
import com.autopilot.service.deployment.module.VerificationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces DeploymentPipelineService.
 * Executes capabilities against an immutable DeploymentManifest.
 */
@Service
public class ModularDeploymentEngine {

    private final List<CompatibilityModule> modules;

    public ModularDeploymentEngine(List<CompatibilityModule> modules) {
        this.modules = modules;
    }

    public void execute(DeploymentManifest manifest) {
        System.out.println("🚀 Starting Modular Deployment Pipeline for: " + manifest.getDeploymentId());
        
        List<CompatibilityModule> activeModules = modules.stream()
                .filter(module -> module.supports(manifest))
                .toList();

        System.out.println("📋 Discovered " + activeModules.size() + " active capabilities.");

        Map<CompatibilityModule, List<Operation>> modulePlans = new HashMap<>();

        try {
            // Phase 1: Plan
            System.out.println("==== Phase 1: PLAN ====");
            for (CompatibilityModule module : activeModules) {
                List<Operation> ops = module.plan(manifest);
                modulePlans.put(module, ops);
            }

            // Phase 2: Apply
            System.out.println("==== Phase 2: APPLY ====");
            for (CompatibilityModule module : activeModules) {
                module.apply(modulePlans.get(module));
            }

            // Phase 3: Verify
            System.out.println("==== Phase 3: VERIFY ====");
            for (CompatibilityModule module : activeModules) {
                VerificationResult result = module.verify();
                if (!result.isSuccessful()) {
                    throw new RuntimeException("Verification failed for module " + module.getClass().getSimpleName() + ": " + result.getErrors());
                }
            }

            System.out.println("✅ Deployment successful!");
        } catch (Exception e) {
            System.err.println("❌ Deployment failed: " + e.getMessage());
            
            // Phase 4: Rollback
            System.out.println("==== Phase 4: ROLLBACK ====");
            for (CompatibilityModule module : activeModules) {
                try {
                    module.rollback();
                } catch (Exception rollbackException) {
                    System.err.println("Failed to rollback module: " + rollbackException.getMessage());
                }
            }
            throw new RuntimeException("Deployment failed and rolled back.", e);
        }
    }
}
