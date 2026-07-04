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
public class DeploymentManifest {
    private String framework;
    private String runtime;
    private String packageManager;
    private String installCommand;
    private String buildCommand;
    private String startCommand;
    private String outputDirectory;
    private String healthCheckPath;
    private int port;
    private Map<String, String> environmentVariables;
}
