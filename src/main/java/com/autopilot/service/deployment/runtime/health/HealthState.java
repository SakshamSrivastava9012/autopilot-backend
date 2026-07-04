package com.autopilot.service.deployment.runtime.health;

public enum HealthState {
    STARTING,
    BOOTSTRAPPING,
    READY,
    LIVE,
    DEGRADED,
    UNHEALTHY,
    FAILED
}
