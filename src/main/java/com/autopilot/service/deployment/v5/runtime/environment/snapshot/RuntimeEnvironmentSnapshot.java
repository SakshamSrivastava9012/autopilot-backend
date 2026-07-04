package com.autopilot.service.deployment.v5.runtime.environment.snapshot;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of generated runtime environment variables.
 *
 * @since V5.4 — ADR-010
 */
@Value
@Builder
public class RuntimeEnvironmentSnapshot {
    String deploymentId;
    Map<String, String> generatedVariables;
    List<String> removedVariables;
    List<String> injectedSecrets;
    String frameworkMapping;
    List<String> warnings;
    Map<String, String> metadata;
}
