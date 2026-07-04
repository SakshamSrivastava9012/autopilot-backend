package com.autopilot.service.deployment.v5.negotiation;

/**
 * How Deployrix should provision this dependency.
 * Consumed by the provisioning layer (V5 Milestone 3+), never by negotiation.
 *
 * @since V5.2
 */
public enum ProviderPreference {
    AUTOMATIC,
    EXISTING_EXTERNAL,
    PLATFORM_MANAGED,
    DOCKER_RUNTIME,
    SKIP
}
