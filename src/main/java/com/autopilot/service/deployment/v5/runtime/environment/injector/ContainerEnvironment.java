package com.autopilot.service.deployment.v5.runtime.environment.injector;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable container environment contract.
 * The output of the Environment Injection Engine — consumed by application deployment nodes.
 *
 * @since V5.4 — ADR-010
 */
@Value
@Builder
public class ContainerEnvironment {
    String environmentId;
    Map<String, String> variables;
    Map<String, String> maskedVariables;
    List<String> secretReferences;
    String framework;
    long generatedAtEpoch;
    Map<String, String> metadata;
}
