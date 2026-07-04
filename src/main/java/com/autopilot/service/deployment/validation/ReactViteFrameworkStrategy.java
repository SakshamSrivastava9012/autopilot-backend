package com.autopilot.service.deployment.validation;

import com.autopilot.analyzer.model.ServiceConfig;
import java.nio.file.Path;
import java.util.List;

public class ReactViteFrameworkStrategy implements FrameworkStrategy {
    private final ServiceConfig service;

    public ReactViteFrameworkStrategy(ServiceConfig service) {
        this.service = service;
    }

    @Override
    public List<String> expectedManifestFiles() {
        return List.of("package.json");
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
        String fw = service.getFramework() != null ? service.getFramework().toLowerCase() : "";
        String rt = service.getRuntime() != null ? service.getRuntime().toUpperCase() : "";
        
        if (fw.contains("next") || fw.contains("nuxt") || fw.contains("ssr") || "SSR".equals(rt)) {
            return service.getPort() != null ? service.getPort() : 3000;
        }
        if (fw.contains("express") || fw.contains("nest") || fw.contains("node") || "NODE_SERVER".equals(rt)) {
            return service.getPort() != null ? service.getPort() : 3000;
        }
        return 80;
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
        if (fw.contains("next") || fw.contains("nuxt") || fw.contains("ssr")) {
            return 180;
        }
        return 45;
    }

    @Override
    public int retryPolicy() {
        return 30;
    }

    @Override
    public List<String> logReadinessMarkers() {
        return List.of("Ready", "Server running", "Listening on", "Local:", "Network:", "ready in", "started");
    }

    @Override
    public List<String> logCrashMarkers() {
        return List.of("FATAL", "Error:", "Address already in use", "Port already in use", "ELIFECYCLE", "sh: 1:");
    }

    @Override
    public List<String> healthEndpoints() {
        return List.of(healthPath(), "/");
    }

    @Override
    public List<String> criticalEnvVars() {
        String fw = service.getFramework() != null ? service.getFramework().toLowerCase() : "";
        if (fw.contains("next") || fw.contains("nuxt")) {
            return List.of("NEXT_PUBLIC_API_URL");
        }
        return List.of();
    }
}

