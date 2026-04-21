package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.util.List;

public class DockerPlugin implements FrameworkPlugin {

    @Override
    public ServiceConfig detect(List<String> files) {

        for (String file : files) {

            if (file.endsWith("Dockerfile")) {

                ServiceConfig service = new ServiceConfig();

                service.setFramework("docker");
                service.setStrategyUsed("DOCKERFILE");
                service.setName("docker-service");
                service.setPath(file.replace("/Dockerfile",""));
                service.setDockerfileExists(true);

                return service;
            }
        }

        return null;
    }
}