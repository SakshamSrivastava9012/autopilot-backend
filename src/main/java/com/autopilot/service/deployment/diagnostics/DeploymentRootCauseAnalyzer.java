package com.autopilot.service.deployment.diagnostics;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DeploymentRootCauseAnalyzer {

    public String analyzeRootCause(List<DeploymentDiagnostics> diagnostics, List<String> validationErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 🚨 Deployment Failure Diagnostics & Root Cause Analysis\n\n");
        sb.append("This report lists operational issues detected across the running containers:\n\n");

        if (validationErrors != null && !validationErrors.isEmpty()) {
            sb.append("## ❌ Validation Failures\n");
            for (String err : validationErrors) {
                sb.append("- ").append(err).append("\n");
            }
            sb.append("\n");
        }

        if (diagnostics != null) {
            for (DeploymentDiagnostics d : diagnostics) {
                sb.append("### 📦 Service: ").append(d.getContainerName()).append("\n");
                if (d.getLastLogs() != null && !d.getLastLogs().isBlank()) {
                    sb.append("#### 📝 Container Logs (Last 20 lines):\n");
                    sb.append("```\n").append(d.getLastLogs()).append("\n```\n");
                }
                if (d.getInspectOutput() != null && !d.getInspectOutput().isBlank()) {
                    sb.append("#### 🔍 Docker Inspect Port Mapping:\n");
                    sb.append("```json\n").append(d.getInspectOutput()).append("\n```\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
