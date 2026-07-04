package com.autopilot.service.deployment.v5.runtime.dependency.report;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Immutable audit report for dependency negotiation.
 *
 * @since V5.6
 */
@Value
@Builder
public class DependencyNegotiationReport {
    String dependencyId;
    String dependencyType;
    String negotiatedProvider;
    String decisionReason;
    List<String> rulesMatched;
    List<String> warnings;
}
