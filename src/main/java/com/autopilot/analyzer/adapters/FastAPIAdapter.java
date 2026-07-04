package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class FastAPIAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        for (String file : relativeFiles) {
            if (file.endsWith("requirements.txt")) {
                try {
                    String content = Files.readString(workspace.resolve(file));
                    if (content.contains("fastapi")) return true;
                } catch (IOException ignored) {}
            } else if (file.endsWith("main.py")) {
                try {
                    String content = Files.readString(workspace.resolve(file));
                    if (content.contains("import fastapi") || content.contains("from fastapi")) return true;
                } catch (IOException ignored) {}
            }
        }
        return false;
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "FastAPI";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "Python FastAPI high-performance web API.";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        return DeploymentManifest.builder()
                .framework("fastapi")
                .runtime("Python 3.10")
                .packageManager("pip")
                .installCommand("pip install -r requirements.txt")
                .buildCommand("")
                .startCommand("uvicorn main:app --host 0.0.0.0 --port 8000")
                .outputDirectory("")
                .healthCheckPath("/health")
                .port(8000)
                .environmentVariables(envVars)
                .build();
    }
}
