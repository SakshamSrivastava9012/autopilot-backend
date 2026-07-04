package com.autopilot.service.deployment.v5.runtime.infrastructure.contract;

import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable contract describing an infrastructure resource to provision.
 * Infrastructure engine executes contracts only — never negotiates or decides.
 *
 * @since V5.4 — ADR-008
 */
@Value
@Builder
public class InfrastructureContract {
    String id;
    String provider;                  // e.g. "aws", "docker", "external", "gcp", "azure"
    InfrastructureResourceType resourceType;
    Map<String, Object> configuration;
    List<String> dependencies;
    List<String> runtimeHints;
    Map<String, String> metadata;
    OwnershipType ownership;          // USER, PLATFORM, EXTERNAL
    String region;
    Map<String, String> tags;
}
