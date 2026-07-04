package com.autopilot.service.deployment.v5.runtime.dependency.validation;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Structured report produced by DependencyValidator.
 *
 * Tracks whether validation was executed pre-flight or post-provisioning based on provider type.
 *
 * @since V5.5 — ADR-009 / ADR-010 Compliance
 */
@Value
@Builder
public class DependencyValidationReport {
    String dependencyId;
    String provider;
    boolean validated;
    boolean deferred;
    String validationPhase; // "PRE_FLIGHT" or "POST_PROVISION"
    String endpointValidated;
    List<String> logs;
    List<String> warnings;
}
