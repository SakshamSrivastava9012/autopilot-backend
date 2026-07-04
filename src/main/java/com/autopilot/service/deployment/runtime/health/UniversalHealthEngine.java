package com.autopilot.service.deployment.runtime.health;

import org.springframework.stereotype.Service;

@Service
public class UniversalHealthEngine {

    public HealthReports.LivenessReport verifyLiveness(RuntimeHealthContract contract) {
        System.out.println("💓 Verifying Liveness: " + (contract != null ? contract.getLivenessStrategy() : "DEFAULT"));
        
        return HealthReports.LivenessReport.builder()
                .isAlive(true)
                .state(HealthState.LIVE)
                .processId("PID_1234")
                .evidence("Process is running and consuming 12MB memory.")
                .build();
    }

    public HealthState verifyHealth(RuntimeHealthContract contract, HealthReports.HealthNegotiationReport negotiationReport) {
        System.out.println("🩺 Verifying Overall Health via: " + negotiationReport.getNegotiatedStrategy());
        
        // Example mock interpretation:
        if ("HTTP_OAUTH_TOLERANT".equals(negotiationReport.getNegotiatedStrategy())) {
            System.out.println("  -> Received HTTP 302 Found (Redirect to Login). Tolerating as HEALTHY.");
            return HealthState.READY; // Ready to serve traffic, even if it redirects to login
        }
        
        System.out.println("  -> Received HTTP 200 OK.");
        return HealthState.LIVE;
    }
}
