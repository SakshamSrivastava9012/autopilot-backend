package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DockerBuilder {

    public String build(ServiceConfig service, String deploymentId) throws Exception {

        String imageName = "autopilot-" + deploymentId;

        Path path = Path.of(service.getPath());

        if (Files.isRegularFile(path)) {
            path = path.getParent();
        }

        String command = "docker build -t " + imageName + " " + path;

        Process process = Runtime.getRuntime().exec(
                new String[]{"bash", "-c", command}
        );

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream())
        );

        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        while ((line = errorReader.readLine()) != null) {
            System.err.println(line);
        }

        int exit = process.waitFor();

        if (exit != 0) {
            throw new RuntimeException("Docker build failed for image: " + imageName);
        }

        return imageName;
    }
}