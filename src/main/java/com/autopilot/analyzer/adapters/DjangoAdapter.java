package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DjangoAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        if (relativeFiles.stream().anyMatch(f -> f.endsWith("manage.py"))) {
            return true;
        }
        for (String file : relativeFiles) {
            if (file.endsWith("requirements.txt")) {
                try {
                    String content = Files.readString(workspace.resolve(file));
                    if (content.contains("django") || content.contains("Django")) return true;
                } catch (IOException ignored) {}
            }
        }
        return false;
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "Django";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "Python Django full-stack web application.";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        return DeploymentManifest.builder()
                .framework("django")
                .runtime("Python 3.10")
                .packageManager("pip")
                .installCommand("pip install -r requirements.txt")
                .buildCommand("python manage.py migrate")
                .startCommand("python manage.py runserver 0.0.0.0:8000")
                .outputDirectory("")
                .healthCheckPath("/")
                .port(8000)
                .environmentVariables(envVars)
                .build();
    }
}
