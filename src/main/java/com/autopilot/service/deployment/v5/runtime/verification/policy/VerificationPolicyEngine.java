package com.autopilot.service.deployment.v5.runtime.verification.policy;

import com.autopilot.service.deployment.v5.runtime.verification.severity.VerificationSeverity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Policy Engine dynamically resolving severity thresholds based on deployment environment policies.
 *
 * @since V5.4 — ADR-012
 */
@Service
public class VerificationPolicyEngine {

    @Value("${deployrix.verification.policy:PRODUCTION}")
    private String activePolicyName;

    public VerificationPolicy getActivePolicy() {
        if ("DEVELOPMENT".equalsIgnoreCase(activePolicyName)) {
            return new DevelopmentPolicy();
        }
        return new ProductionPolicy();
    }

    private static class DevelopmentPolicy implements VerificationPolicy {
        @Override public String policyName() { return "DEVELOPMENT"; }
        @Override
        public VerificationSeverity evaluateFinding(String findingType, VerificationSeverity defaultSeverity) {
            if (defaultSeverity == VerificationSeverity.ERROR) return VerificationSeverity.WARNING;
            return defaultSeverity;
        }
    }

    private static class ProductionPolicy implements VerificationPolicy {
        @Override public String policyName() { return "PRODUCTION"; }
        @Override
        public VerificationSeverity evaluateFinding(String findingType, VerificationSeverity defaultSeverity) {
            return defaultSeverity;
        }
    }
}
