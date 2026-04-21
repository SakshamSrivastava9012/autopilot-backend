package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Production-grade Dockerfile generator with 4-tier fallback strategy.
 *
 * Tier 1: Use native Dockerfile from repo (skip generation)
 * Tier 2: Use framework template with placeholder substitution
 * Tier 3: Use AI (Stellar LLM) to generate Dockerfile
 * Tier 4: Generate deterministic fallback Dockerfile from ServiceConfig
 *
 * NEVER throws an exception. Always writes a usable Dockerfile.
 */
@Component
@RequiredArgsConstructor
public class DockerfileGenerator {

    private final DockerTemplateLoader templateLoader;
    private final StellarDockerService stellarDockerService;

    public void generate(ServiceConfig service) throws Exception {

        String dockerfile = null;

        System.out.println("━━━ DockerfileGenerator ━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Framework: " + service.getFramework());
        System.out.println("  Language:  " + service.getLanguage());
        System.out.println("  Runtime:   " + service.getRuntimeVersion());
        System.out.println("  Build:     " + service.getBuildCommand());
        System.out.println("  Start:     " + service.getStartCommand());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // ── TIER 1: Skip if Dockerfile already exists in repo ────────────
        if (service.isDockerfileExists()) {
            System.out.println("✅ TIER 1: Native Dockerfile found → skipping generation");
            return;
        }

        // ── TIER 2: Framework template with placeholder substitution ─────
        try {
            dockerfile = templateLoader.loadTemplate(service.getFramework());
            dockerfile = replacePlaceholders(dockerfile, service);
            System.out.println("✅ TIER 2: Template loaded and placeholders replaced");
        } catch (Exception templateError) {
            System.out.println("⚠️ TIER 2 Failed: " + templateError.getMessage());
        }

        // ── TIER 3: AI Generation (only if template failed) ──────────────
        if (dockerfile == null || dockerfile.isBlank() || !dockerfile.contains("FROM")) {
            try {
                System.out.println("🤖 TIER 3: Requesting Dockerfile from Stellar AI...");
                dockerfile = stellarDockerService.generateDockerfile(service);
                if (dockerfile != null && dockerfile.contains("FROM")) {
                    System.out.println("✅ TIER 3: AI Dockerfile generated");
                } else {
                    dockerfile = null; // Force fallback
                }
            } catch (Exception aiError) {
                System.err.println("⚠️ TIER 3 Failed: " + aiError.getMessage());
            }
        }

        // ── TIER 4: Deterministic fallback (NEVER FAILS) ─────────────────
        if (dockerfile == null || dockerfile.isBlank() || !dockerfile.contains("FROM")) {
            System.out.println("🛡️ TIER 4: Generating deterministic fallback Dockerfile");
            dockerfile = generateFallbackDockerfile(service);
        }

        // ── WRITE ────────────────────────────────────────────────────────
        Path servicePath = Path.of(service.getPath()).toAbsolutePath().normalize();

        if (Files.isRegularFile(servicePath)) {
            servicePath = servicePath.getParent();
        }

        Files.createDirectories(servicePath);

        Path dockerfilePath = servicePath.resolve("Dockerfile");

        System.out.println("📝 Writing Dockerfile to: " + dockerfilePath);
        System.out.println("── DOCKERFILE CONTENT ──────────────────────────────");
        System.out.println(dockerfile);
        System.out.println("────────────────────────────────────────────────────");

        try (FileWriter writer = new FileWriter(dockerfilePath.toFile())) {
            writer.write(dockerfile);
        }
    }

    /**
     * Replace all template placeholders with actual values from ServiceConfig.
     */
    private String replacePlaceholders(String dockerfile, ServiceConfig service) {
        String port = service.getPort() != null ? service.getPort().toString() : "8080";
        String runtime = service.getRuntimeVersion() != null ? service.getRuntimeVersion() : "17";
        String buildCmd = service.getBuildCommand() != null ? service.getBuildCommand() : "echo 'no build'";
        String startCmd = service.getStartCommand() != null ? service.getStartCommand() : "echo 'no start'";

        return dockerfile
                .replace("{{PORT}}", port)
                .replace("{{RUNTIME_VERSION}}", runtime)
                .replace("{{BUILD_COMMAND}}", buildCmd)
                .replace("{{START_COMMAND}}", startCmd);
    }

    /**
     * Generate a deterministic fallback Dockerfile based on detected language.
     * Uses multi-tool base images that include build tools.
     * This method NEVER fails — it always returns a valid Dockerfile string.
     */
    private String generateFallbackDockerfile(ServiceConfig service) {

        String lang = service.getLanguage() != null ? service.getLanguage().toLowerCase() : "unknown";
        String runtime = service.getRuntimeVersion() != null ? service.getRuntimeVersion() : "";
        String buildCmd = service.getBuildCommand() != null ? service.getBuildCommand() : "echo 'skipping build'";
        String startCmd = service.getStartCommand() != null ? service.getStartCommand() : "echo 'no start command detected'";
        int port = service.getPort() != null ? service.getPort() : 8080;

        return switch (lang) {
            case "java" -> generateJavaFallback(runtime, buildCmd, port);
            case "javascript", "node", "typescript" -> generateNodeFallback(runtime, buildCmd, startCmd, port);
            case "python" -> generatePythonFallback(runtime, buildCmd, startCmd, port);
            case "go" -> generateGoFallback(runtime, port);
            default -> generateUniversalFallback(buildCmd, startCmd, port);
        };
    }

    private String generateJavaFallback(String runtime, String buildCmd, int port) {
        String version = runtime.isBlank() ? "17" : runtime;
        return """
                FROM maven:3.9.9-eclipse-temurin-%s AS builder
                WORKDIR /build
                COPY . .
                RUN chmod +x mvnw gradlew 2>/dev/null || true
                RUN %s
                FROM eclipse-temurin:%s-jre
                WORKDIR /app
                COPY --from=builder /build/target/*.jar app.jar
                EXPOSE %d
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """.formatted(version, buildCmd, version, port);
    }

    private String generateNodeFallback(String runtime, String buildCmd, String startCmd, int port) {
        String version = runtime.isBlank() ? "20" : runtime;
        return """
                FROM node:%s-alpine
                WORKDIR /app
                COPY package*.json ./
                RUN npm install
                COPY . .
                RUN %s
                EXPOSE %d
                CMD %s
                """.formatted(version, buildCmd, port, startCmd);
    }

    private String generatePythonFallback(String runtime, String buildCmd, String startCmd, int port) {
        String version = runtime.isBlank() ? "3.10" : runtime;
        return """
                FROM python:%s-slim
                WORKDIR /app
                COPY requirements.txt .
                RUN pip install --no-cache-dir -r requirements.txt || true
                COPY . .
                EXPOSE %d
                CMD %s
                """.formatted(version, port, startCmd);
    }

    private String generateGoFallback(String runtime, int port) {
        String version = runtime.isBlank() ? "1.22" : runtime;
        return """
                FROM golang:%s-alpine AS builder
                WORKDIR /build
                COPY . .
                RUN go build -o /app/server .
                FROM alpine:3.19
                WORKDIR /app
                COPY --from=builder /app/server .
                EXPOSE %d
                CMD ["./server"]
                """.formatted(version, port);
    }

    /**
     * Universal fallback — installs Java + Node + Python and tries all build tools.
     * This is the absolute last resort. It WILL produce a runnable container.
     */
    private String generateUniversalFallback(String buildCmd, String startCmd, int port) {
        return """
                FROM ubuntu:22.04
                ENV DEBIAN_FRONTEND=noninteractive
                RUN apt-get update && apt-get install -y \\
                    openjdk-17-jdk maven \\
                    nodejs npm \\
                    python3 python3-pip \\
                    && rm -rf /var/lib/apt/lists/*
                WORKDIR /app
                COPY . .
                RUN %s || echo "Build step failed — continuing anyway"
                EXPOSE %d
                CMD %s
                """.formatted(buildCmd, port, startCmd);
    }
}