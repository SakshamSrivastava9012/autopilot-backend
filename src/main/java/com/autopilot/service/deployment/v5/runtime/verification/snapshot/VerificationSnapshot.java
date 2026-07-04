package com.autopilot.service.deployment.v5.runtime.verification.snapshot;

import com.autopilot.service.deployment.v5.runtime.verification.contract.ModuleResult;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all runtime verification outputs and quality scores.
 *
 * @since V5.4 — ADR-012
 */
@Value
@Builder
public class VerificationSnapshot {
    String deploymentId;
    int qualityScore;
    boolean overallSuccess;
    List<ModuleResult> moduleResults;
    List<String> criticalFailures;
    List<String> warnings;
    List<String> timeline;
    Map<String, String> metadata;
}
