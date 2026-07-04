package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ReactCRAAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        return AdapterUtils.containsDependency(workspace, relativeFiles, "react-scripts");
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "React (CRA)";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "Create React App building to build/";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        String pm = AdapterUtils.detectPackageManager(workspace, relativeFiles);
        return DeploymentManifest.builder()
                .framework("react-cra")
                .runtime("Static")
                .packageManager(pm)
                .installCommand(AdapterUtils.getInstallCommand(pm))
                .buildCommand(pm + " run build")
                .startCommand("npx serve -s build -l 3000")
                .outputDirectory("build")
                .healthCheckPath("/")
                .port(3000)
                .environmentVariables(envVars)
                .build();
    }
}
