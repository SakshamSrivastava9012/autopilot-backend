package com.autopilot.service.deployment.v5.runtime.startup.negotiation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Manages active EngineProfile across Deployrix subsystems.
 *
 * @since V5.4 — ADR-011
 */
@Service
public class EngineProfileManager {

    private final EngineProfile activeProfile;

    public EngineProfileManager(@Value("${deployrix.engine.profile:V5_PRODUCTION}") String profileStr) {
        EngineProfile parsed;
        try {
            parsed = EngineProfile.valueOf(profileStr.toUpperCase());
        } catch (Exception e) {
            parsed = EngineProfile.V5_PRODUCTION;
        }
        this.activeProfile = parsed;
        System.out.println("🚀 Deployrix Engine Profile Manager active profile: [" + this.activeProfile + "]");
    }

    public EngineProfile getActiveProfile() {
        return activeProfile;
    }

    public boolean isV5Active() {
        return activeProfile.isV5Active();
    }
}
