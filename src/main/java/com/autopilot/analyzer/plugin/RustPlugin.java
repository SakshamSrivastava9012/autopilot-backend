package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.util.List;

public class RustPlugin implements FrameworkPlugin {

    @Override
    public ServiceConfig detect(List<String> files) {

        for (String file : files) {
            if (file.endsWith("Cargo.toml")) {

                ServiceConfig service = new ServiceConfig();

                service.setFramework("rust");
                service.setLanguage("rust");
                service.setStrategyUsed("TEMPLATE");
                service.setRuntimeVersion("1.77");
                service.setConfidence(85);
                service.setName("rust-service");

                String path = file.replace("/Cargo.toml", "").replace("Cargo.toml", ".");
                service.setPath(path);

                service.setBuildCommand("cargo build --release");
                service.setStartCommand("./target/release/*");
                service.setPort(8080);

                service.setDockerfileExists(
                        files.contains(path + "/Dockerfile") || files.contains("Dockerfile")
                );

                return service;
            }
        }
        return null;
    }
}
