package com.autopilot.service.deployment.runtime.health;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;

@Service
public class HealthNegotiationEngine {

    public HealthReports.HealthNegotiationReport negotiate(RuntimeHealthContract baseContract, Map<String, Object> runtimeContext) {
        System.out.println("🤝 Negotiating Health Strategies...");
        
        String negotiatedStrategy = "HTTP_200"; // fallback
        String rationale = "Generic fallback applied. No specific framework detected.";
        
        if (runtimeContext != null) {
            if (runtimeContext.containsKey("DOCKER_HEALTHCHECK")) {
                negotiatedStrategy = "DOCKER_HEALTHCHECK";
                rationale = "Docker natively defines a health check. Trusting container definitions.";
            } else if (Boolean.TRUE.equals(runtimeContext.get("HAS_ACTUATOR"))) {
                negotiatedStrategy = "HTTP_ACTUATOR";
                rationale = "Spring Boot Actuator specifically detected. Polling /actuator/health.";
            } else if (Boolean.TRUE.equals(runtimeContext.get("HAS_OAUTH"))) {
                negotiatedStrategy = "HTTP_OAUTH_TOLERANT";
                rationale = "OAuth detected. Tolerating 302/401 redirect bounces as HEALTHY.";
            } else if (runtimeContext.containsKey("CONFIGURED_HEALTH_ENDPOINT")) {
                negotiatedStrategy = "HTTP_CONFIGURED";
                rationale = "Explicit health endpoint detected from metadata.";
            } else if (Boolean.TRUE.equals(runtimeContext.get("HAS_HTTP"))) {
                negotiatedStrategy = "HTTP_ROOT";
                rationale = "Web framework detected. Checking HTTP / (root) for 2xx/3xx/401.";
            } else if (Boolean.TRUE.equals(runtimeContext.get("HAS_TCP"))) {
                negotiatedStrategy = "TCP_LISTENER";
                rationale = "No HTTP detected. Falling back to successful TCP port binding.";
            } else {
                negotiatedStrategy = "PROCESS_ALIVE";
                rationale = "No network endpoints discovered. Verifying main process is alive.";
            }
        }
        
        return HealthReports.HealthNegotiationReport.builder()
                .expectedStrategy(baseContract != null ? baseContract.getHealthStrategy() : "UNKNOWN")
                .negotiatedStrategy(negotiatedStrategy)
                .rationale(rationale)
                .detectionMetadata(runtimeContext != null ? runtimeContext : new HashMap<>())
                .build();
    }
}
