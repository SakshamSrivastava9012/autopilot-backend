package com.autopilot.service.deployment.v5.runtime.verification.module;

import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.verification.contract.ModuleResult;
import com.autopilot.service.deployment.v5.runtime.verification.policy.VerificationPolicy;
import com.autopilot.service.deployment.v5.runtime.verification.severity.VerificationSeverity;

/**
 * Interface for independent, capability-driven verification modules.
 *
 * @since V5.4 — ADR-012
 */
public interface VerificationModule {

    String id();

    boolean supports(RuntimeContext context);

    VerificationSeverity severity();

    ModuleResult verify(RuntimeContext context, VerificationPolicy policy);
}
