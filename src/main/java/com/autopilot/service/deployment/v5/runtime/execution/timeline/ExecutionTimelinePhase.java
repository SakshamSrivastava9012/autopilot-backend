package com.autopilot.service.deployment.v5.runtime.execution.timeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionTimelinePhase {
    private String phaseName;
    private long startTime;
    private long endTime;
    private long durationMs;
    private String status; // RUNNING, COMPLETED, FAILED
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    private int retries;
    @Builder.Default
    private List<String> events = new ArrayList<>();
    @Builder.Default
    private List<String> logs = new ArrayList<>();
}
