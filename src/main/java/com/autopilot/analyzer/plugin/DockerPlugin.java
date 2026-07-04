package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DockerPlugin implements FrameworkPlugin {

    @Override
    public List<ServiceConfig> detect(List<String> files) {
        List<ServiceConfig> services = new ArrayList<>();

        for (String file : files) {
            if (file.endsWith("Dockerfile") && !file.contains("node_modules")) {
                ServiceConfig service = new ServiceConfig();
                service.setFramework("docker");
                service.setStrategyUsed("DOCKERFILE");
                String name = deriveServiceName(file, "docker-service");
                service.setName(name);
                Path parent = Path.of(file).getParent();
                service.setPath(parent == null ? "." : parent.toString());
                service.setDockerfileExists(true);
                services.add(service);
            }
        }

        return services;
    }

    private String deriveServiceName(String file, String defaultName) {
        Path parent = Path.of(file).getParent();
        if (parent == null || parent.getFileName() == null) {
            return defaultName;
        }
        return parent.getFileName().toString();
    }
}