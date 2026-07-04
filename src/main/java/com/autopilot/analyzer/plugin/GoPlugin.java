package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GoPlugin implements FrameworkPlugin {

    @Override
    public List<ServiceConfig> detect(List<String> files) {
        List<ServiceConfig> services = new ArrayList<>();

        for (String file : files) {
            if (file.endsWith("go.mod") && !file.contains("node_modules")) {
                ServiceConfig service = new ServiceConfig();
                service.setFramework("go");
                service.setLanguage("go");
                service.setStrategyUsed("TEMPLATE");
                service.setRuntimeVersion("1.22");
                service.setConfidence(85);
                
                String name = deriveServiceName(file, "go-service");
                service.setName(name);

                Path parent = Path.of(file).getParent();
                String pathStr = parent == null ? "." : parent.toString();
                service.setPath(pathStr);

                service.setBuildCommand("go build -o server .");
                service.setStartCommand("./server");
                service.setPort(8080);

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

