package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class GoAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        return relativeFiles.stream().anyMatch(f -> f.endsWith("go.mod"));
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "Go";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "Go backend service.";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        return DeploymentManifest.builder()
                .framework("go")
                .runtime("Go 1.22")
                .packageManager("go-modules")
                .installCommand("go mod download")
                .buildCommand("go build -o main .")
                .startCommand("./main")
                .outputDirectory("")
                .healthCheckPath("/health")
                .port(8080)
                .environmentVariables(envVars)
                .build();
    }
}
