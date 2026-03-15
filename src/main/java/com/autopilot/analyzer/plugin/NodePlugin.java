package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.util.List;

public class NodePlugin implements FrameworkPlugin {


    @Override
    public ServiceConfig detect(List<String> files) {

        for (String file : files) {

            if (file.endsWith("package.json")) {

                ServiceConfig service = new ServiceConfig();

                service.setFramework("node");
                service.setName("node-service");
                service.setPath(file.replace("/package.json",""));
                service.setBuildCommand("npm install");
                service.setStartCommand("npm start");
                service.setPort(3000);

                service.setDockerfileExists(
                        files.contains(service.getPath() + "/Dockerfile")
                );

                return service;
            }
        }

        return null;
    }


}