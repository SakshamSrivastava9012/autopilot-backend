package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.util.List;

public class GoPlugin implements FrameworkPlugin {

    @Override
    public ServiceConfig detect(List<String> files) {

        for (String file : files) {
            if (file.endsWith("go.mod")) {

                ServiceConfig service = new ServiceConfig();

                service.setFramework("go");
                service.setLanguage("go");
                service.setStrategyUsed("TEMPLATE");
                service.setRuntimeVersion("1.22");
                service.setConfidence(85);
                service.setName("go-service");

                String path = file.replace("/go.mod", "").replace("go.mod", ".");
                service.setPath(path);

                service.setBuildCommand("go build -o server .");
                service.setStartCommand("./server");
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
