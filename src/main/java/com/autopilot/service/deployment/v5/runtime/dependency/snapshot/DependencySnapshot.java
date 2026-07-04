package com.autopilot.service.deployment.v5.runtime.dependency.snapshot;

import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of provisioned runtime dependencies.
 *
 * @since V5.4 — ADR-009
 */
@Value
@Builder
public class DependencySnapshot {
    String deploymentId;
    List<RuntimeDependency> dependencies;
    List<String> credentialReferences;
    List<String> endpoints;
    OwnershipType ownership;
    String runtimeState;
    Map<String, String> metadata;
}
