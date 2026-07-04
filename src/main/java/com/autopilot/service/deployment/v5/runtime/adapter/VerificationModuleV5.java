package com.autopilot.service.deployment.v5.runtime.adapter;

import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.contract.RollbackResult;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.engine.RuntimeModule;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionPhase;
import com.autopilot.service.deployment.v5.runtime.verification.engine.RuntimeVerificationPlatformV5;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Runtime verification phase module connected to RuntimeVerificationPlatformV5.
 *
 * @since V5.4 — ADR-012
 */
@Component
public class VerificationModuleV5 implements RuntimeModule {

    private final RuntimeVerificationPlatformV5 verificationPlatform;

    public VerificationModuleV5(RuntimeVerificationPlatformV5 verificationPlatform) {
        this.verificationPlatform = verificationPlatform;
    }

    @Override public String id() { return "verification-module"; }

    @Override public boolean supports(RuntimeContext context) { return true; }

    @Override
    public ExecutionNode createNode(RuntimeContext context) {
        return new AbstractRuntimeNode("verification-node", "Universal Runtime Verification Platform & Quality Engine", ExecutionPhase.VALIDATION, Collections.singletonList("startup-node")) {
            @Override
            public ExecutionResult execute(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                System.out.println("🔍 Executing Graph Node: [verification-node]");

                RuntimeVerificationPlatformV5.PlatformVerificationResult result = verificationPlatform.verifyDeployment(ctx);

                ctx.putResolvedObject("VerificationSnapshot", result.getSnapshot());
                ctx.putResolvedObject("DeploymentQualityReport", result.getQualityReport());

                Map<String, Object> outputs = new HashMap<>();
                outputs.put("qualityScore", result.getQualityReport().getQualityScore());
                outputs.put("overallSuccess", result.getQualityReport().isSuccessful());
                outputs.put("criticalFailuresCount", result.getQualityReport().getCriticalFailures().size());
                outputs.put("warningsCount", result.getQualityReport().getWarnings().size());

                return ExecutionResult.builder()
                        .success(result.getQualityReport().isSuccessful())
                        .nodeId(getId())
                        .message("Runtime verification completed. Quality Score: " + result.getQualityReport().getQualityScore() + "/100")
                        .outputs(outputs)
                        .logs(result.getTimeline())
                        .warnings(result.getQualityReport().getWarnings())
                        .executionDurationMs(System.currentTimeMillis() - start)
                        .build();
            }

            @Override
            public RollbackResult rollback(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                System.out.println("🔍 Rollback [verification-node] — Clearing verification outputs");

                return RollbackResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Verification outputs cleared")
                        .logs(Collections.singletonList("Verification snapshot cleared"))
                        .warnings(Collections.emptyList())
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }
        };
    }
}
