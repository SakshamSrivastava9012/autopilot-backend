package com.autopilot.service.deployment.v5.runtime.startup.negotiation;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable contract specifying startup, readiness, and liveness probe rules.
 *
 * @since V5.4 — ADR-011
 */
@Value
@Builder
public class StartupContract {
    String contractId;
    String serviceId;
    String startupStrategy;            // DOCKER_HEALTHCHECK, HTTP, HTTPS, TCP, PROCESS_ALIVE, OAUTH_REDIRECT, WEBSOCKET, GRAPHQL, SSE, CUSTOM
    String readinessEndpoint;
    String healthEndpoint;
    int expectedPort;
    List<Integer> expectedStatusCodes; // e.g. [200, 201, 202, 204, 301, 302, 303, 307, 308, 401, 403]
    long readinessTimeoutMs;
    long healthTimeoutMs;
    long maxAdaptiveExtensionMs;
    Map<String, String> metadata;
}
