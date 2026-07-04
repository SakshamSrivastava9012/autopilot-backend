package com.autopilot.service.deployment.v5.runtime.verification.contract;

import com.autopilot.service.deployment.v5.runtime.verification.severity.VerificationSeverity;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Output result from a single capability-specific verification module.
 *
 * @since V5.4 — ADR-012
 */
@Value
@Builder
public class ModuleResult {
    String moduleId;
    boolean passed;
    VerificationSeverity severity;
    String summary;
    List<String> findings;
    List<String> warnings;
    Map<String, Object> metrics;
}
