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

        String command = "docker build --no-cache -t " + imageName + " " + path;

        System.out.println("🚀 Running: " + command);

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);

        // 🔥 FIX: merge stdout + stderr (prevents deadlock)
        pb.redirectErrorStream(true);

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        int exit = process.waitFor();

        if (exit != 0) {
            throw new RuntimeException("❌ Docker build failed for image: " + imageName);
        }

        System.out.println("✅ Docker image built: " + imageName);

        return imageName;
    }
}