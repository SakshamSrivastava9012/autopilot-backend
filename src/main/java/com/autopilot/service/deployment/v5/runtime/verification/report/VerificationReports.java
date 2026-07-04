package com.autopilot.service.deployment.v5.runtime.verification.report;

import com.autopilot.service.deployment.v5.runtime.verification.contract.ModuleResult;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Dashboard-ready verification reports.
 *
 * @since V5.4 — ADR-012
 */
public class VerificationReports {

    @Value
    @Builder
    public static class DeploymentQualityReport {
        String deploymentId;
        int qualityScore;
        boolean successful;
        List<String> criticalFailures;
        List<String> warnings;
        List<String> recommendations;
        List<ModuleResult> moduleResults;
    }

    @Value
    @Builder
    public static class CapabilityReport {
        String moduleId;
        boolean passed;
        String severity;
        String summary;
    }
}
