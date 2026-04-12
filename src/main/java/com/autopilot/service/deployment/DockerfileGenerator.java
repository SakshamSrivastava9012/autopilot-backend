package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class DockerfileGenerator {

    private final DockerTemplateLoader templateLoader;
    private final StellarDockerService stellarDockerService;

    public void generate(ServiceConfig service) throws Exception {

        String dockerfile;

        try {
            System.out.println("🚀 Using Stellar LLM for Dockerfile generation");

            dockerfile = stellarDockerService.generateDockerfile(service);

            System.out.println("📦 LLM Dockerfile generated successfully");

        } catch (Exception e) {

            System.out.println("⚠️ Stellar failed, fallback to template");
            System.out.println("Reason: " + e.getMessage());

            dockerfile = templateLoader.loadTemplate(service.getFramework());
            dockerfile = dockerfile.replace("{{PORT}}", service.getPort().toString());
        }

        // 🔥 Resolve correct service path
        Path servicePath = Path.of(service.getPath()).toAbsolutePath().normalize();

        if (Files.isRegularFile(servicePath)) {
            servicePath = servicePath.getParent();
        }

        Files.createDirectories(servicePath);

        Path dockerfilePath = servicePath.resolve("Dockerfile");

        // 🔥 DEBUG: print final Dockerfile
        System.out.println("📦 FINAL DOCKERFILE:\n" + dockerfile);

        try (FileWriter writer = new FileWriter(dockerfilePath.toFile())) {
            writer.write(dockerfile);
        }

        System.out.println("✅ Dockerfile written at: " + dockerfilePath);
    }
}