package com.autopilot.service.deployment.v5.runtime.infrastructure.snapshot;

import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.RuntimeInfrastructure;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all provisioned infrastructure.
 *
 * @since V5.4 — ADR-008
 */
@Value
@Builder
public class InfrastructureSnapshot {
    String deploymentId;
    List<RuntimeInfrastructure> resources;
    List<String> identifiers;
    List<String> providers;
    long snapshotTimeEpoch;
    String region;
    boolean allAvailable;
    Map<String, String> metadata;
}
