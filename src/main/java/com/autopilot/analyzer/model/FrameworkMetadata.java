package com.autopilot.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrameworkMetadata {
    private String name;
    private FrameworkType frameworkType;
    private RuntimeType runtimeType;
    private PackageManager packageManager;
    private String buildCommand;
    private String startCommand;
    private String outputDirectory;
    private int port;
    private String healthCheckPath;
    private String language;
    private String defaultRuntimeVersion;
    private boolean dockerfileExists;
    private Map<String, String> environmentVariables;
    private String basePath;

    public void validate() {
        java.util.Objects.requireNonNull(name, "FrameworkMetadata name cannot be null");
        java.util.Objects.requireNonNull(frameworkType, "FrameworkMetadata frameworkType cannot be null");
        java.util.Objects.requireNonNull(runtimeType, "FrameworkMetadata runtimeType cannot be null");
        java.util.Objects.requireNonNull(packageManager, "FrameworkMetadata packageManager cannot be null");
    }
}
