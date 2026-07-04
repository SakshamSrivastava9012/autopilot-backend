package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class NestJSAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        return AdapterUtils.containsDependency(workspace, relativeFiles, "@nestjs/core");
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "NestJS";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "NestJS enterprise Node.js framework building to dist/";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        String pm = AdapterUtils.detectPackageManager(workspace, relativeFiles);
        return DeploymentManifest.builder()
                .framework("nestjs")
                .runtime("Node")
                .packageManager(pm)
                .installCommand(AdapterUtils.getInstallCommand(pm))
                .buildCommand(pm + " run build")
                .startCommand("node dist/main.js")
                .outputDirectory("dist")
                .healthCheckPath("/health")
                .port(3000)
                .environmentVariables(envVars)
                .build();
    }
}
