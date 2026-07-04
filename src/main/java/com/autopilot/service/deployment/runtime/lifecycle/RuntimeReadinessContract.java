package com.autopilot.service.deployment.runtime.lifecycle;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class RuntimeReadinessContract {
    private String readinessStrategy; // e.g. "HTTP", "TCP", "PROCESS", "DOCKER_HEALTHCHECK"
    private String healthStrategy;
    
    private long startupTimeoutMs;
    private long readinessTimeoutMs;
    
    private Set<Integer> acceptableStatusCodes; // e.g. 200, 204, 301, 302, 401, 403
    
    private List<Integer> expectedPorts;
    private List<String> expectedProcesses;
    
    private boolean requiresDatabase;
    private boolean requiresProxy;
    private boolean requiresOAuth;
}
