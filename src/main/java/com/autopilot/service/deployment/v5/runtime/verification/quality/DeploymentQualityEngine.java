package com.autopilot.service.deployment.v5.runtime.verification.quality;

import com.autopilot.service.deployment.v5.runtime.verification.contract.ModuleResult;
import com.autopilot.service.deployment.v5.runtime.verification.report.VerificationReports;
import com.autopilot.service.deployment.v5.runtime.verification.severity.VerificationSeverity;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Computes structured deployment quality scores (100 down to 0) and generates DeploymentQualityReport.
 *
 * @since V5.4 — ADR-012
 */
@Service
public class DeploymentQualityEngine {

    public VerificationReports.DeploymentQualityReport evaluateQuality(String deploymentId, List<ModuleResult> results) {
        System.out.println("📈 Deployment Quality Engine — Computing quality score for deployment [" + deploymentId + "]...");

        int score = 100;
        boolean hasCriticalFailure = false;
        List<String> criticalFailures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        for (ModuleResult res : results) {
            if (!res.isPassed()) {
                if (res.getSeverity() == VerificationSeverity.CRITICAL) {
                    hasCriticalFailure = true;
                    score -= 50;
                    criticalFailures.add(res.getModuleId() + ": " + res.getSummary());
                } else if (res.getSeverity() == VerificationSeverity.ERROR) {
                    score -= 15;
                    warnings.add(res.getModuleId() + ": " + res.getSummary());
                } else if (res.getSeverity() == VerificationSeverity.WARNING) {
                    score -= 5;
                    warnings.add(res.getModuleId() + ": " + res.getSummary());
                }
            }
            if (res.getWarnings() != null) {
                warnings.addAll(res.getWarnings());
            }
        }

        score = Math.max(0, score);
        if (score >= 90) {
            recommendations.add("Deployment quality is EXCELLENT. Ready for full traffic promotion.");
        } else if (score >= 75) {
            recommendations.add("Deployment quality is ACCEPTABLE with non-critical warnings.");
        } else {
            recommendations.add("Deployment quality degraded. Address non-critical findings.");
        }

        return VerificationReports.DeploymentQualityReport.builder()
                .deploymentId(deploymentId)
                .qualityScore(score)
                .successful(!hasCriticalFailure)
                .criticalFailures(criticalFailures)
                .warnings(warnings)
                .recommendations(recommendations)
                .moduleResults(results)
                .build();
    }
}
