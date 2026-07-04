package com.autopilot.service.deployment.v5.runtime.dependency.report;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Immutable audit report for dependency provisioning.
 *
 * @since V5.6
 */
@Value
@Builder
public class ProvisioningReport {
    String dependencyId;
    String provider;
    boolean success;
    long durationMs;
    String runtimeStatus;
    List<String> logs;
    List<String> warnings;
}
