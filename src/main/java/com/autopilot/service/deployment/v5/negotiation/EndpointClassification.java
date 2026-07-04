package com.autopilot.service.deployment.v5.negotiation;

/**
 * Classification of a detected endpoint's environment context.
 *
 * @since V5.2
 */
public enum EndpointClassification {
    LOCALHOST,
    PRIVATE_NETWORK,
    PUBLIC_IP,
    CLOUD_DATABASE,
    DOCKER_SERVICE,
    UNKNOWN
}
