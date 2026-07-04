package com.autopilot.service.deployment.runtime.lifecycle;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RuntimeLifecycleReport {
    private boolean isHealthy;
    private ApplicationRuntimeState currentState;
    private RuntimeReadinessContract readinessContract;
    private StartupFailureReport failureReport;
    private RuntimeTransitionTimeline timeline;
}
