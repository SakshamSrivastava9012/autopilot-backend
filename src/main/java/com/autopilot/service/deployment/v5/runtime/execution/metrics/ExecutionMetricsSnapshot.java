package com.autopilot.service.deployment.v5.runtime.execution.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionMetricsSnapshot {
    private String sessionId;
    private long repoCloneTimeMs;
    private long detectionTimeMs;
    private long negotiationTimeMs;
    private long buildTimeMs;
    private long pushTimeMs;
    private long terraformTimeMs;
    private long infrastructureTimeMs;
    private long dockerPullTimeMs;
    private long containerStartupTimeMs;
    private long springBootStartupTimeMs;
    private long frontendStartupTimeMs;
    private long verificationTimeMs;
    private long totalDeploymentTimeMs;
}
