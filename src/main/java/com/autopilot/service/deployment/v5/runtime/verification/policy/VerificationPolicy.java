package com.autopilot.service.deployment.v5.runtime.verification.policy;

import com.autopilot.service.deployment.v5.runtime.verification.severity.VerificationSeverity;

/**
 * Defines evaluation rules for verification findings based on environment policy.
 *
 * @since V5.4 — ADR-012
 */
public interface VerificationPolicy {

    String policyName();

    VerificationSeverity evaluateFinding(String findingType, VerificationSeverity defaultSeverity);
}
