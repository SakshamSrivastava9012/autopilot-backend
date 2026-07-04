package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class LaravelAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        if (relativeFiles.stream().anyMatch(f -> f.endsWith("artisan"))) {
            return true;
        }
        for (String file : relativeFiles) {
            if (file.endsWith("composer.json")) {
                try {
                    String content = Files.readString(workspace.resolve(file));
                    if (content.contains("laravel/framework")) return true;
                } catch (IOException ignored) {}
            }
        }
        return false;
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "Laravel";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "PHP Laravel framework enterprise application.";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        return DeploymentManifest.builder()
                .framework("laravel")
                .runtime("PHP 8.2")
                .packageManager("composer")
                .installCommand("composer install --no-dev")
                .buildCommand("php artisan migrate --force")
                .startCommand("php artisan serve --host=0.0.0.0 --port=8000")
                .outputDirectory("public")
                .healthCheckPath("/")
                .port(8000)
                .environmentVariables(envVars)
                .build();
    }
}
