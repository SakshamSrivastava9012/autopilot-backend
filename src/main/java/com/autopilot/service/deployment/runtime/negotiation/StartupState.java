package com.autopilot.service.deployment.runtime.negotiation;

public enum StartupState {
    IMAGE_READY,
    CONTAINER_CREATED,
    CONTAINER_RUNNING,
    MAIN_PROCESS_RUNNING,
    PORT_DISCOVERY,
    HTTP_DISCOVERY,
    READINESS_DISCOVERY,
    READY
}
