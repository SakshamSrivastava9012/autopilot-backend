package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class NuxtAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        return AdapterUtils.containsDependency(workspace, relativeFiles, "nuxt");
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "Nuxt";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "Nuxt SSR application building to .output/";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        String pm = AdapterUtils.detectPackageManager(workspace, relativeFiles);
        return DeploymentManifest.builder()
                .framework("nuxt")
                .runtime("Node")
                .packageManager(pm)
                .installCommand(AdapterUtils.getInstallCommand(pm))
                .buildCommand(pm + " run build")
                .startCommand("node .output/server/index.mjs")
                .outputDirectory(".output")
                .healthCheckPath("/")
                .port(3000)
                .environmentVariables(envVars)
                .build();
    }
}
