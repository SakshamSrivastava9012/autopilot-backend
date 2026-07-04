package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ViteAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        return AdapterUtils.containsDependency(workspace, relativeFiles, "vite");
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "Vite";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "Vite static application building to dist/";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        String pm = AdapterUtils.detectPackageManager(workspace, relativeFiles);
        return DeploymentManifest.builder()
                .framework("vite")
                .runtime("Static")
                .packageManager(pm)
                .installCommand(AdapterUtils.getInstallCommand(pm))
                .buildCommand(pm + " run build")
                .startCommand("npx serve -s dist -l 3000")
                .outputDirectory("dist")
                .healthCheckPath("/")
                .port(3000)
                .environmentVariables(envVars)
                .build();
    }
}
