package com.autopilot.service.deployment.runtime.dependency;

import org.springframework.stereotype.Service;

@Service
public class DependencyNegotiationEngine {

    public DependencyReports.NegotiationReport negotiate(DependencyDescriptor descriptor, String userPreference) {
        System.out.println("🤝 Negotiating Dependency: " + descriptor.getName() + " (preference=" + userPreference + ")");
        
        String negotiatedProvider = descriptor.getProvider();
        String reason = "Negotiated based on user preference or detected URI.";
        
        if ("AUTOMATIC".equals(negotiatedProvider) || negotiatedProvider == null || "Run Docker Container".equalsIgnoreCase(negotiatedProvider)) {
            if (descriptor.getConnectionUri() != null) {
                negotiatedProvider = "EXISTING_EXTERNAL";
                reason = "Detected existing external URI. Reusing existing infrastructure.";
            } else if ("Provision Platform Managed".equalsIgnoreCase(userPreference)) {
                negotiatedProvider = "PLATFORM_MANAGED";
                reason = "Using Platform Managed database according to user preference.";
            } else if ("Run Docker Container".equalsIgnoreCase(userPreference)) {
                negotiatedProvider = "DOCKER_RUNTIME";
                reason = "Using Docker Runtime database according to user preference.";
            } else {
                negotiatedProvider = "DOCKER_RUNTIME";
                reason = "No external URI or explicit preference detected. Defaulting to Docker Runtime.";
            }
        }
        
        return DependencyReports.NegotiationReport.builder()
                .dependencyName(descriptor.getName())
                .detectedProvider("AUTOMATIC".equals(descriptor.getProvider()) ? "UNKNOWN" : descriptor.getProvider())
                .negotiatedProvider(negotiatedProvider)
                .reason(reason)
                .build();
    }

    public DependencyReports.NegotiationReport negotiate(DependencyDescriptor descriptor) {
        return negotiate(descriptor, "AUTOMATIC");
    }
}
