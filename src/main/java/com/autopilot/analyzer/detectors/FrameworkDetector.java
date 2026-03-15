package com.autopilot.analyzer.detectors;

import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.analyzer.plugin.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FrameworkDetector {

    private final List<FrameworkPlugin> plugins = List.of(
            new NodePlugin(),
            new SpringBootPlugin(),
            new com.autopilot.analyzer.plugins.PythonPlugin(),
            new DockerPlugin()
    );

    public List<ServiceConfig> detect(List<String> files) {

        List<ServiceConfig> services = new ArrayList<>();

        for (FrameworkPlugin plugin : plugins) {

            ServiceConfig service = plugin.detect(files);

            if (service != null) {
                services.add(service);
            }
        }

        return services;
    }
    public List<ServiceConfig> detectWorkspace(Path workspace) throws Exception {

        List<String> files =
                Files.walk(workspace)
                        .filter(Files::isRegularFile)
                        .map(path -> workspace.relativize(path).toString())
                        .collect(Collectors.toList());

        return detect(files);
    }
}