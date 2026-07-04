package com.autopilot.service.deployment.v5.runtime.startup.negotiation;

/**
 * Unified Engine Profile controlling all Deployrix V5 engine subsystems.
 * Replaces fragmented individual feature flags.
 *
 * @since V5.4 — ADR-011
 */
public enum EngineProfile {
    LEGACY,
    V5_EXPERIMENTAL,
    V5_STAGING,
    V5_PRODUCTION;

    public boolean isV5Active() {
        return this != LEGACY;
    }
}
