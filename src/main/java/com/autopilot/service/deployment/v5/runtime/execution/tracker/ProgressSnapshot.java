package com.autopilot.service.deployment.v5.runtime.execution.tracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProgressSnapshot {
    private String sessionId;
    private DeploymentStage stage;
    private int percentage;
    private long estimatedRemainingTimeMs;
    private String currentOperation;
    private long lastOutputTimestamp;
    private long durationMs;
}
