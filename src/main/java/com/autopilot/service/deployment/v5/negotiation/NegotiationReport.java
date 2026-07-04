package com.autopilot.service.deployment.v5.negotiation;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Immutable report of a single dependency negotiation decision.
 * Provides full auditability for the Deployrix dashboard.
 *
 * @since V5.2 — ADR-005
 */
@Value
@Builder
public class NegotiationReport {
    String dependencyType;
    String decision;            // e.g. "EXISTING_EXTERNAL", "DOCKER_RUNTIME"
    int confidence;
    String reason;
    List<String> evidence;
    List<String> rulesMatched;
    List<String> warnings;
}
