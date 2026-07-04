package com.autopilot.service.deployment.v5.runtime.execution.stall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StallReport {
    private String sessionId;
    private String stage;
    private long lastProgressTimestamp;
    private long stallDurationMs;
    private boolean stalled;
    private String reason;
    private String suggestedFix;
}
