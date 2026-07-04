package com.autopilot.service.deployment.runtime.lifecycle;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StartupFailureReport {
    private ApplicationRuntimeState lifecycleState;
    private ApplicationRuntimeState failedTransition;
    private ApplicationRuntimeState lastSuccessfulState;
    
    private long timestamp;
    private String dockerLogs;
    private String dockerInspect;
    
    private int exitCode;
    private int restartCount;
    private boolean oomKilled;
    
    private String cpuUsage;
    private String memoryUsage;
}
