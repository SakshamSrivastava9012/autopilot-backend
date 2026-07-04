package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ExpressAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        return AdapterUtils.containsDependency(workspace, relativeFiles, "express")
                && !AdapterUtils.containsDependency(workspace, relativeFiles, "@nestjs/core");
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "Express.js";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "Node.js Express application.";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        String pm = AdapterUtils.detectPackageManager(workspace, relativeFiles);
        return DeploymentManifest.builder()
                .framework("express")
                .runtime("Node")
                .packageManager(pm)
                .installCommand(AdapterUtils.getInstallCommand(pm))
                .buildCommand("")
                .startCommand("node index.js")
                .outputDirectory("")
                .healthCheckPath("/health")
                .port(3000)
                .environmentVariables(envVars)
                .build();
    }
}
