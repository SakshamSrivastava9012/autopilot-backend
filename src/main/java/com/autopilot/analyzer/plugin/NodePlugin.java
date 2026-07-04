package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NodePlugin implements FrameworkPlugin {

    @Override
    public List<ServiceConfig> detect(List<String> files) {
        List<ServiceConfig> services = new ArrayList<>();

        for (String file : files) {
            if (file.endsWith("package.json") && !file.contains("node_modules")) {
                ServiceConfig service = new ServiceConfig();
                service.setLanguage("javascript");
                service.setStrategyUsed("TEMPLATE");
                service.setRuntimeVersion("20");
                
                Path parent = Path.of(file).getParent();
                String pathStr = parent == null ? "." : parent.toString();
                service.setPath(pathStr);
                
                String name = deriveServiceName(file, "node-service");
                service.setName(name);
                service.setPort(3000);
                service.setDockerfileExists(files.contains(pathStr + "/Dockerfile") || files.contains("Dockerfile"));
                
                try {
                    String content = Files.readString(Path.of(file));
                    if (content.contains("\"next\"")) {
                        service.setFramework("next");
                        service.setBuildCommand("npm run build");
                        service.setStartCommand("npm start");
                    } else if (content.contains("\"react-scripts\"") || content.contains("\"vite\"") || content.contains("\"react\"")) {
                        service.setFramework("react");
                        service.setBuildCommand("npm run build");
                        service.setStartCommand("npx serve -s build -l 3000"); 
                    } else if (content.contains("\"@angular/core\"") || files.stream().anyMatch(f -> f.endsWith("angular.json") && f.startsWith(pathStr))) {
                        service.setFramework("angular");
                        service.setBuildCommand("npm run build");
                        service.setStartCommand("npx serve -s dist/* -l 3000");
                    } else if (content.contains("\"vue\"")) {
                        service.setFramework("vue");
                        service.setBuildCommand("npm run build");
                        service.setStartCommand("npx serve -s dist -l 3000");
                    } else if (content.contains("\"express\"") || content.contains("\"@nestjs/core\"") || content.contains("\"fastify\"") || content.contains("\"koa\"")) {
                        service.setFramework("node");
                        if (content.contains("\"build\"")) {
                            service.setBuildCommand("npm run build");
                        } else {
                            service.setBuildCommand("echo 'No build step'");
                        }
                        if (content.contains("\"start\"")) {
                            service.setStartCommand("npm start");
                        } else {
                            service.setStartCommand("node index.js");
                        }
                    } else {
                        service.setFramework("node");
                        service.setBuildCommand("npm run build");
                        service.setStartCommand("npm start");
                    }
                } catch (Exception e) {
                    service.setFramework("node");
                    service.setBuildCommand("npm run build");
                    service.setStartCommand("npm start");
                }
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