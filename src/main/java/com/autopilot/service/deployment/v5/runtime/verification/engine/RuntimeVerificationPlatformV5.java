package com.autopilot.service.deployment.v5.runtime.verification.engine;

import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.verification.contract.ModuleResult;
import com.autopilot.service.deployment.v5.runtime.verification.module.VerificationModule;
import com.autopilot.service.deployment.v5.runtime.verification.policy.VerificationPolicy;
import com.autopilot.service.deployment.v5.runtime.verification.policy.VerificationPolicyEngine;
import com.autopilot.service.deployment.v5.runtime.verification.quality.DeploymentQualityEngine;
import com.autopilot.service.deployment.v5.runtime.verification.report.VerificationReports;
import com.autopilot.service.deployment.v5.runtime.verification.severity.VerificationSeverity;
import com.autopilot.service.deployment.v5.runtime.verification.snapshot.VerificationSnapshot;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Universal Runtime Verification Platform V5.
 *
 * Evaluates deployment quality through independent capability-driven verification modules
 * after the application reaches the STABLE state.
 *
 * Uses VerificationPolicyEngine to dynamically resolve severity thresholds.
 *
 * It NEVER restarts containers, modifies runtime, redeploys, or provisions infrastructure.
 *
 * @since V5.4 — ADR-012
 */
@Service
public class RuntimeVerificationPlatformV5 {

    private final List<VerificationModule> modules;
    private final VerificationPolicyEngine policyEngine;
    private final DeploymentQualityEngine qualityEngine;

    public RuntimeVerificationPlatformV5(List<VerificationModule> modules,
                                         VerificationPolicyEngine policyEngine,
                                         DeploymentQualityEngine qualityEngine) {
        this.modules = modules != null ? modules : new ArrayList<>();
        this.policyEngine = policyEngine;
        this.qualityEngine = qualityEngine;
        System.out.println("🔍 Universal Runtime Verification Platform V5 initialized with "
                + this.modules.size() + " independent verification modules.");
    }

    public PlatformVerificationResult verifyDeployment(RuntimeContext context) {
        String depId = context.getDeploymentId() != null ? context.getDeploymentId() : "dep-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("🔍 Runtime Verification Platform V5 — Evaluating deployment quality for [" + depId + "]...");

        long start = System.currentTimeMillis();
        VerificationPolicy activePolicy = policyEngine.getActivePolicy();
        List<ModuleResult> moduleResults = new ArrayList<>();
        List<String> timeline = new ArrayList<>();

        timeline.add("Verification Started (Policy: " + activePolicy.policyName() + ")");

        for (VerificationModule module : modules) {
            if (module.supports(context)) {
                timeline.add("Module Started: " + module.id());
                ModuleResult res = module.verify(context, activePolicy);
                moduleResults.add(res);
                timeline.add("Module Finished: " + module.id() + " (Passed: " + res.isPassed() + ")");
            }
        }

        timeline.add("Quality Calculated");
        VerificationReports.DeploymentQualityReport qualityReport = qualityEngine.evaluateQuality(depId, moduleResults);
        timeline.add("Deployment Finished");

        VerificationSnapshot snapshot = VerificationSnapshot.builder()
                .deploymentId(depId)
                .qualityScore(qualityReport.getQualityScore())
                .overallSuccess(qualityReport.isSuccessful())
                .moduleResults(moduleResults)
                .criticalFailures(qualityReport.getCriticalFailures())
                .warnings(qualityReport.getWarnings())
                .timeline(timeline)
                .metadata(Collections.singletonMap("policyName", activePolicy.policyName()))
                .build();

        return new PlatformVerificationResult(snapshot, qualityReport, moduleResults, timeline);
    }

    @lombok.Value
    public static class PlatformVerificationResult {
        VerificationSnapshot snapshot;
        VerificationReports.DeploymentQualityReport qualityReport;
        List<ModuleResult> moduleResults;
        List<String> timeline;
    }
}
