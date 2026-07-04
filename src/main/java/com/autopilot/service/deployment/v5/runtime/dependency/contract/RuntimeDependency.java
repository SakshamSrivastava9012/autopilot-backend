package com.autopilot.service.deployment.v5.runtime.dependency.contract;

import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Immutable runtime dependency instance.
 * Keyed by unique dependency ID (e.g. "primary-db", "analytics-db", "session-cache")
 * to support multiple instances of the same dependency type.
 *
 * @since V5.4 — ADR-009
 */
@Value
@Builder
public class RuntimeDependency {
    String id;                           // Unique ID: "primary-db", "analytics-db", "cache-redis"
    RuntimeDependencyType dependencyType;
    String provider;
    DependencyLifecycle runtimeStatus;
    String runtimeEndpoint;
    Map<String, String> runtimeMetadata;
    String credentialReference;
    String healthReference;
    OwnershipType ownership;
    long createdAtEpoch;
}
