package com.autopilot.service.deployment.runtime.dependency;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RuntimeDependencyContract {
    // Tracks state: PROVISIONED, HEALTHY, READY, WAITING, RETRYING, FAILED, DEGRADED
    private String dependencyState;
    private List<DependencyDescriptor> dependencies;
    private long startupTimeoutMs;
    
    // V4.4 Additions
    private Map<String, String> negotiatedEnvVars;
    private List<String> preDeployCommands;
    private DependencyReports.CredentialValidationReport validationReport;
}
