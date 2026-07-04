package com.autopilot.service.deployment.runtime.health;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RuntimeHealthContract {
    private String startupStrategy; // e.g., PROCESS_ALIVE
    private String readinessStrategy; // e.g., PORT_BOUND, HTTP_200
    private String livenessStrategy; // e.g., PROCESS_ALIVE, DOCKER_HEALTHCHECK
    private String healthStrategy; // e.g., ACTUATOR, CUSTOM_HTTP
    
    private long startupTimeoutMs;
    private long readinessTimeoutMs;
    private int maxRetries;
    
    private List<Integer> expectedStatusCodes;
    
    private boolean expectsAuthentication; // e.g., JWT, Basic
    private boolean expectsOAuthRedirects; // Tolerates 302/303 to login
    private boolean requiresDatabase;
    private boolean requiresExternalDependencies; // Redis, S3, etc.
}
