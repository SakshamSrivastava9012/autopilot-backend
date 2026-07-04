package com.autopilot.service.deployment.runtime.lifecycle;

public enum ApplicationRuntimeState {
    INFRASTRUCTURE_READY,
    IMAGE_AVAILABLE,
    CONTAINER_CREATED,
    CONTAINER_RUNNING,
    PROCESS_STARTED,
    APPLICATION_BOOTSTRAPPING,
    PORT_BOUND,
    READINESS_CONFIRMED,
    HEALTH_AVAILABLE,
    READY,
    SERVING_TRAFFIC
}
