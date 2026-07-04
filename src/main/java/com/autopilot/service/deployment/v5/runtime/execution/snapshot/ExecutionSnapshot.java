package com.autopilot.service.deployment.v5.runtime.execution.snapshot;

import com.autopilot.service.deployment.v5.runtime.execution.report.ExecutionReports;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionSnapshot {
    private String sessionId;
    private long timestamp;
    private ExecutionReports.DeploymentExecutionReport executionReport;
}
