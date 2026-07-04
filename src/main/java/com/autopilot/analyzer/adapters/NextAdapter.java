package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class NextAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        return AdapterUtils.containsDependency(workspace, relativeFiles, "next");
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "Next.js";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "Next.js SSR application building to .next/";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        String pm = AdapterUtils.detectPackageManager(workspace, relativeFiles);
        return DeploymentManifest.builder()
                .framework("next")
                .runtime("Node")
                .packageManager(pm)
                .installCommand(AdapterUtils.getInstallCommand(pm))
                .buildCommand(pm + " run build")
                .startCommand("npm start")
                .outputDirectory(".next")
                .healthCheckPath("/api/health")
                .port(3000)
                .environmentVariables(envVars)
                .build();
    }
}
