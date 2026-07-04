package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PythonPlugin implements FrameworkPlugin {

    @Override
    public List<ServiceConfig> detect(List<String> files) {
        List<ServiceConfig> services = new ArrayList<>();
        Set<String> processedPaths = new HashSet<>();

        for (String file : files) {
            if ((file.endsWith("requirements.txt") || file.endsWith("pyproject.toml") || file.endsWith("manage.py"))
                    && !file.contains("node_modules")) {

                Path parent = Path.of(file).getParent();
                String pathStr = parent == null ? "." : parent.toString();
                if (processedPaths.contains(pathStr)) {
                    continue;
                }
                processedPaths.add(pathStr);

                ServiceConfig service = new ServiceConfig();
                service.setFramework("python");
                service.setLanguage("python");
                service.setStrategyUsed("TEMPLATE");
                service.setRuntimeVersion("3.10");

                String name = deriveServiceName(file, "python-service");
                service.setName(name);
                service.setPath(pathStr);

                if (file.endsWith("manage.py")) {
                    service.setBuildCommand("pip install -r requirements.txt || true");
                    service.setStartCommand("python manage.py runserver 0.0.0.0:8000");
                    service.setPort(8000);
                } else {
                    service.setBuildCommand("pip install -r requirements.txt || pip install poetry && poetry install || true");
                    service.setStartCommand("python app.py");
                    service.setPort(5000);
                }

                service.setDockerfileExists(
                        files.contains(pathStr + "/Dockerfile") || files.contains("Dockerfile")
                );

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