package com.autopilot.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentContext {
    private String deploymentId;
    private Path workspace;
    private List<String> relativeFiles;
    private FrameworkMetadata detectedFramework;
    private Map<String, String> environmentVariables;

    public void validate() {
        java.util.Objects.requireNonNull(deploymentId, "deploymentId cannot be null");
        java.util.Objects.requireNonNull(workspace, "workspace cannot be null");
        java.util.Objects.requireNonNull(relativeFiles, "relativeFiles list cannot be null");
        java.util.Objects.requireNonNull(detectedFramework, "detectedFramework cannot be null");
        java.util.Objects.requireNonNull(environmentVariables, "environmentVariables map cannot be null");
    }
}
