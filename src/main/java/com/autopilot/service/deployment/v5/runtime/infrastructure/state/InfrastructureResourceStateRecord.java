package com.autopilot.service.deployment.v5.runtime.infrastructure.state;

import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Persisted state record for an infrastructure resource (Terraform-state equivalent).
 *
 * @since V5.4 — ADR-008
 */
@Value
@Builder
public class InfrastructureResourceStateRecord {
    String internalResourceId;
    String deploymentId;
    String provider;
    String cloudId;
    OwnershipType ownership;
    long createdAtEpoch;
    String deletionPolicy;       // RETAIN, DELETE, ARCHIVE
    String rollbackPolicy;       // DELETE_IF_PLATFORM, PRESERVE
    Map<String, String> tags;
    Map<String, String> metadata;
}
