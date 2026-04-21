package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.util.List;

public class PythonPlugin implements FrameworkPlugin {

    @Override
    public ServiceConfig detect(List<String> files) {

        for (String file : files) {

            if (file.endsWith("requirements.txt")) {

                ServiceConfig service = new ServiceConfig();

                service.setFramework("python");
                service.setLanguage("python");
                service.setStrategyUsed("TEMPLATE");
                service.setRuntimeVersion("3.10");
                service.setName("python-service");

                service.setPath(file.replace("/requirements.txt",""));

                service.setBuildCommand("pip install -r requirements.txt");

                service.setStartCommand("python app.py");

                service.setPort(5000);

                service.setDockerfileExists(
                        files.contains(service.getPath() + "/Dockerfile")
                );

                return service;
            }
        }

        return null;
    }
}