package com.autopilot.service.deployment.v5.runtime.execution.diagnostics;

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
public class DeploymentFailureReport {

    public enum FailureCategory {
        DockerPullFailed,
        ContainerCrash,
        OOMKilled,
        SpringBootFailed,
        HealthTimeout,
        DependencyTimeout,
        NetworkFailure,
        ECRAuthenticationFailure,
        SSMDisconnected,
        TerraformFailure,
        StalledExecution,
        Unknown
    }

    private String sessionId;
    private FailureCategory failureCategory;
    private String stage;
    private String rootCause;
    private String suggestedFix;
    private String logsSnippet;
    @Builder.Default
    private List<String> diagnosticDetails = new ArrayList<>();
}
