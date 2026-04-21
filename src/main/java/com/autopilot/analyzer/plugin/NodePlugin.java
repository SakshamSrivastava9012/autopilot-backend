package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.util.List;

public class NodePlugin implements FrameworkPlugin {


    @Override
    public ServiceConfig detect(List<String> files) {

        for (String file : files) {

            if (file.endsWith("package.json") && !file.contains("node_modules")) {

                ServiceConfig service = new ServiceConfig();
                service.setLanguage("javascript");
                service.setStrategyUsed("TEMPLATE");
                service.setRuntimeVersion("20");
                service.setName("node-service");
                service.setPath(file.replace("/package.json",""));
                service.setPort(3000);
                service.setDockerfileExists(files.contains(service.getPath() + "/Dockerfile"));

                // Read package.json to determine the exact framework and commands
                try {
                    String content = java.nio.file.Files.readString(java.nio.file.Path.of(file));
                    
                    if (content.contains("\"next\"")) {
                        service.setFramework("next");
                        service.setBuildCommand("npm run build");
                        service.setStartCommand("npm start");
                    } else if (content.contains("\"react-scripts\"") || content.contains("\"vite\"")) {
                        service.setFramework("react");
                        service.setBuildCommand("npm run build");
                        // Serve static files using serve or similar for production
                        service.setStartCommand("npx serve -s build -l 3000"); 
                    } else {
                        service.setFramework("node");
                        // Generic Node app might not have a build step
                        if (content.contains("\"build\"")) {
                            service.setBuildCommand("npm run build");
                        } else {
                            service.setBuildCommand("echo 'No build step detected'");
                        }
                        
                        if (content.contains("\"start\"")) {
                            service.setStartCommand("npm start");
                        } else {
                            service.setStartCommand("node index.js");
                        }
                    }
                } catch (Exception e) {
                    // Fallback if we can't read package.json
                    service.setFramework("node");
                    service.setBuildCommand("npm run build");
                    service.setStartCommand("npm start");
                }

                return service;
            }
        }

        return null;
    }


}