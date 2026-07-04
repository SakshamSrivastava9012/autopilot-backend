package com.autopilot.service.deployment.v5.runtime.environment.resolver;

import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Immutable contract describing connection details for a single runtime dependency.
 * The only source of truth for application connection resolution.
 *
 * @since V5.4 — ADR-010
 */
@Value
@Builder
public class RuntimeConnectionContract {
    String connectionId;
    String dependencyId;
    String dependencyType;
    String provider;
    String endpoint;
    String protocol;
    String host;
    int port;
    String database;
    String username;
    String password;
    String uri;
    boolean ssl;
    boolean tls;
    String certificateReference;
    String authenticationType;
    String authentication;
    String healthEndpoint;
    OwnershipType ownership;
    Map<String, String> metadata;
}
