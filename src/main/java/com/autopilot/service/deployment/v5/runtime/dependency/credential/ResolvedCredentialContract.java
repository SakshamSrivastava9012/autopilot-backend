package com.autopilot.service.deployment.v5.runtime.dependency.credential;

import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable credential contract resolved by CredentialResolver.
 * Credentials come from providers or user configs — NEVER invented default strings.
 *
 * @since V5.4 — ADR-009
 */
@Value
@Builder
public class ResolvedCredentialContract {
    String dependencyId;            // e.g. "primary-db", "analytics-db", "session-cache"
    String username;
    String password;
    String database;
    String host;
    int port;
    String uri;
    String provider;
    OwnershipType ownership;
    String generatedBy;             // DOCKER_RUNTIME, CLOUD_PROVIDER, USER_CONFIG, SECRETS_MANAGER
    boolean rotationSupported;
    String secretReference;
}
