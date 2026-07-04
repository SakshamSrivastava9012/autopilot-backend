package com.autopilot.service.deployment.v5.runtime.verification.severity;

/**
 * Structured deployment failure types for verification.
 *
 * @since V5.4 — ADR-012
 */
public enum DeploymentFailureType {
    BROWSER_CRASH,
    API_UNAVAILABLE,
    CRITICAL_ASSET_MISSING,
    CONTAINER_CRASH,
    DEPENDENCY_FAILURE,
    OAUTH_FAILURE,
    ROUTE_FAILURE,
    SECURITY_FAILURE,
    UNKNOWN
}
