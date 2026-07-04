package com.autopilot.service.deployment.v5.runtime.environment.injector;

/**
 * Lifecycle states of environment injection.
 *
 * @since V5.4 — ADR-010
 */
public enum EnvironmentLifecycle {
    CREATED,
    MAPPED,
    SANITIZED,
    SECRETS_RESOLVED,
    INJECTED,
    VERIFIED,
    FAILED,
    ROLLBACK
}
