package com.autopilot.service.deployment.v5.runtime.execution.diagnostics;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Execution Diagnostics Engine.
 * Responsibilities:
 * - Classifies deployment failures (DockerPullFailed, ContainerCrash, OOMKilled, SpringBootFailed, HealthTimeout, DependencyTimeout, NetworkFailure, ECRAuthenticationFailure, SSMDisconnected, TerraformFailure, StalledExecution).
 * - Generates structured Failure and Diagnostic Reports.
 * - Operates purely as an observation and classification layer.
 */
@Service
public class ExecutionDiagnosticsEngine {

    public DeploymentFailureReport classifyFailure(String sessionId, String stage, String rawError, String logsSnippet) {
        if (sessionId == null) sessionId = "unknown-session";
        if (rawError == null) rawError = "Unknown deployment error";
        String errUpper = rawError.toUpperCase();

        DeploymentFailureReport.FailureCategory category;
        String rootCause;
        String suggestedFix;
        List<String> details = new ArrayList<>();

        if (errUpper.contains("PULL") || errUpper.contains("MANIFEST UNKNOWN") || errUpper.contains("IMAGE NOT FOUND")) {
            category = DeploymentFailureReport.FailureCategory.DockerPullFailed;
            rootCause = "Docker image pull failed: image or tag does not exist or access is restricted.";
            suggestedFix = "Verify docker image URI, repository permissions, and tag existence.";
        } else if (errUpper.contains("137") || errUpper.contains("OOM") || errUpper.contains("OUT OF MEMORY")) {
            category = DeploymentFailureReport.FailureCategory.OOMKilled;
            rootCause = "Container was terminated by Out-Of-Memory (OOM) killer.";
            suggestedFix = "Increase EC2 instance memory or adjust JVM heap settings (-Xmx).";
        } else if (errUpper.contains("SPRING") || errUpper.contains("APPLICATIONCONTEXT") || errUpper.contains("BEAN CREATION")) {
            category = DeploymentFailureReport.FailureCategory.SpringBootFailed;
            rootCause = "Spring Boot application startup failed during context initialization.";
            suggestedFix = "Check Spring application logs for missing bean definitions or invalid configuration properties.";
        } else if (errUpper.contains("STALL") || errUpper.contains("NO PROGRESS")) {
            category = DeploymentFailureReport.FailureCategory.StalledExecution;
            rootCause = "Deployment execution stalled with no progress for extended duration.";
            suggestedFix = "Check host SSM connection, container logs, and system resources.";
        } else if (errUpper.contains("HEALTH") || errUpper.contains("READINESS TIMEOUT")) {
            category = DeploymentFailureReport.FailureCategory.HealthTimeout;
            rootCause = "Container health check timed out before endpoint returned HTTP 200 OK.";
            suggestedFix = "Verify application startup time, health endpoint path, and internal database connectivity.";
        } else if (errUpper.contains("AUTHENTICATION") || errUpper.contains("DENIED") || errUpper.contains("ECR")) {
            category = DeploymentFailureReport.FailureCategory.ECRAuthenticationFailure;
            rootCause = "Authentication token for container registry / AWS ECR was rejected or expired.";
            suggestedFix = "Refresh ECR auth token or check AWS IAM policies for ECR get-authorization-token.";
        } else if (errUpper.contains("SSM") || errUpper.contains("DISCONNECTED")) {
            category = DeploymentFailureReport.FailureCategory.SSMDisconnected;
            rootCause = "AWS SSM Agent connection lost or target EC2 instance went offline.";
            suggestedFix = "Verify EC2 instance status, SSM Agent service status, and IAM instance profile.";
        } else if (errUpper.contains("TERRAFORM") || errUpper.contains("HCL")) {
            category = DeploymentFailureReport.FailureCategory.TerraformFailure;
            rootCause = "Terraform infrastructure provisioning command failed.";
            suggestedFix = "Check Terraform HCL syntax, provider AWS credentials, and resource quota limits.";
        } else {
            category = DeploymentFailureReport.FailureCategory.Unknown;
            rootCause = rawError;
            suggestedFix = "Inspect full execution logs for unhandled exception details.";
        }

        details.add("Diagnostic classified at stage: " + (stage != null ? stage : "UNSPECIFIED"));
        details.add("Error signature: " + rawError);

        return DeploymentFailureReport.builder()
                .sessionId(sessionId)
                .failureCategory(category)
                .stage(stage != null ? stage : "UNKNOWN")
                .rootCause(rootCause)
                .suggestedFix(suggestedFix)
                .logsSnippet(logsSnippet != null ? logsSnippet : "")
                .diagnosticDetails(details)
                .build();
    }
}
