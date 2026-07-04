package com.autopilot.service.deployment.validation;

import com.autopilot.analyzer.model.ServiceConfig;
import java.nio.file.Path;
import java.util.List;

public class LaravelFrameworkStrategy implements FrameworkStrategy {
    private final ServiceConfig service;

    public LaravelFrameworkStrategy(ServiceConfig service) {
        this.service = service;
    }

    @Override
    public List<String> expectedManifestFiles() {
        return List.of("composer.json");
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
        return 60;
    }

    @Override
    public int retryPolicy() {
        return 20;
    }
}

