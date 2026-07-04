package com.autopilot.service.deployment.intelligence.v5.model;

import lombok.Builder;
import lombok.Value;

/**
 * Describes a runtime route discovered from controller annotations,
 * router definitions, or filesystem-based routing.
 *
 * @since V5
 */
@Value
@Builder
public class RouteDefinition {
    String path;             // e.g. "/api/auth/register"
    String method;           // GET, POST, PUT, DELETE, ALL
    String serviceId;        // Which service owns this route
    String source;           // e.g. "AuthController.java:32"
    boolean isDynamic;       // e.g. /users/[id] — should never be probed
    boolean isApi;
    boolean requiresAuth;
}
