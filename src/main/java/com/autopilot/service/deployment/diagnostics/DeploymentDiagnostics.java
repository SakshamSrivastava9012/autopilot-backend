package com.autopilot.service.deployment.diagnostics;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DeploymentDiagnostics {
    private String containerName;
    private String inspectOutput;
    private String lastLogs;
    private List<String> validationErrors;
    private Map<String, Object> environmentVars;
    private String cpuMemoryStats;
}
