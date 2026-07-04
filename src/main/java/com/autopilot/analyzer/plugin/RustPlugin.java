package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RustPlugin implements FrameworkPlugin {

    @Override
    public List<ServiceConfig> detect(List<String> files) {
        List<ServiceConfig> services = new ArrayList<>();

        for (String file : files) {
            if (file.endsWith("Cargo.toml") && !file.contains("node_modules")) {
                ServiceConfig service = new ServiceConfig();
                service.setFramework("rust");
                service.setLanguage("rust");
                service.setStrategyUsed("TEMPLATE");
                service.setRuntimeVersion("1.77");
                service.setConfidence(85);
                
                String name = deriveServiceName(file, "rust-service");
                service.setName(name);

                Path parent = Path.of(file).getParent();
                String pathStr = parent == null ? "." : parent.toString();
                service.setPath(pathStr);

                service.setBuildCommand("cargo build --release");
                service.setStartCommand("./target/release/*");
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

