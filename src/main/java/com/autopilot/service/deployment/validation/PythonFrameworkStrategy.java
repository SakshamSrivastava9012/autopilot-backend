package com.autopilot.service.deployment.validation;

import com.autopilot.analyzer.model.ServiceConfig;
import java.nio.file.Path;
import java.util.List;

public class PythonFrameworkStrategy implements FrameworkStrategy {
    private final ServiceConfig service;

    public PythonFrameworkStrategy(ServiceConfig service) {
        this.service = service;
    }

    @Override
    public List<String> expectedManifestFiles() {
        return List.of("requirements.txt", "pyproject.toml", "Pipfile");
    }

    @Override
    public BuildCommand buildCommand() {
        return new BuildCommand(service.getBuildCommand());
    }

    @Override
    public DockerStrategy dockerStrategy() {
        String root = service.getServiceRoot();
        return new DockerStrategy(
            Path.of(root).resolve("Dockerfile").toAbsolutePath().normalize().toString(),
            root
        );
    }

    @Override
    public int containerPort() {
        return service.getPort() != null ? service.getPort() : 8000;
    }

    @Override
    public String healthPath() {
        if (service.getDeploymentManifest() != null && service.getDeploymentManifest().getHealthCheckPath() != null) {
            return service.getDeploymentManifest().getHealthCheckPath();
        }
        return "/";
    }

    @Override
    public String protocol() {
        return "HTTP";
    }

    @Override
    public List<Integer> expectedStatusCodes() {
        return List.of(200, 204, 301, 302, 404);
    }

    @Override
    public int startupTimeout() {
        String fw = service.getFramework() != null ? service.getFramework().toLowerCase() : "";
        if (fw.contains("django")) {
            return 180;
        }
        if (fw.contains("fastapi")) {
            return 120;
        }
        return 90;
    }

    @Override
    public int retryPolicy() {
        return 30;
    }

    @Override
    public List<String> logReadinessMarkers() {
        return List.of("Uvicorn running", "Booting worker", "Starting development server", "Listening on", "running on");
    }

    @Override
    public List<String> logCrashMarkers() {
        return List.of("Traceback", "Exception", "Error:", "Address already in use", "Port already in use", "CRITICAL");
    }

    @Override
    public List<String> healthEndpoints() {
        return List.of(healthPath(), "/");
    }

    @Override
    public List<String> criticalEnvVars() {
        return List.of("DATABASE_URL");
    }
}

