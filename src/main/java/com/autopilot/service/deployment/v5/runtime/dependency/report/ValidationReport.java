package com.autopilot.service.deployment.v5.runtime.dependency.report;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Immutable audit report for dependency post-provision validation.
 *
 * @since V5.6
 */
@Value
@Builder
public class ValidationReport {
    String dependencyId;
    String provider;
    boolean validated;
    boolean deferred;
    String validationPhase;
    String endpointValidated;
    List<String> logs;
    List<String> warnings;
}
