package com.autopilot.service.deployment.intelligence.v5.detector.impl;

import com.autopilot.service.deployment.intelligence.v5.detector.DetectorResultV5;
import com.autopilot.service.deployment.intelligence.v5.detector.RepositoryDetector;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects deployment-driving capabilities (STATIC_SITE, SPA, SSR, REST_API, etc.).
 * These capabilities — not framework names — determine how Deployrix deploys.
 */
@Component
public class CapabilityDetectorV5 implements RepositoryDetector {
    @Override public String name() { return "CapabilityDetector"; }
    @Override public String version() { return "5.0.0"; }

    @Override
    public List<DetectorResultV5> detect(String repositoryPath) {
        List<DetectorResultV5> results = new ArrayList<>();
        File root = new File(repositoryPath);

        // SSR detection
        if (new File(root, "next.config.js").exists() || new File(root, "next.config.mjs").exists()
                || new File(root, "nuxt.config.ts").exists()) {
            results.add(result("SSR", 0.95, "next.config / nuxt.config", "SSR framework configuration found"));
        }

        // SPA detection
        if (new File(root, "vite.config.ts").exists() || new File(root, "angular.json").exists()) {
            results.add(result("SPA", 0.90, "vite.config / angular.json", "Single-page application build config found"));
        }

        // Static site detection
        boolean hasIndex = new File(root, "index.html").exists();
        boolean hasPublic = new File(root, "public/index.html").exists();
        if (hasIndex || hasPublic) {
            results.add(result("STATIC_SITE", hasPublic ? 0.80 : 0.70, "index.html", "Static HTML entry point found"));
        }

        // REST API detection (Spring / Express / NestJS)
        if (new File(root, "pom.xml").exists()) {
            results.add(result("REST_API", 0.85, "pom.xml", "JVM server project — likely exposes HTTP API"));
        }

        // Docker support
        if (new File(root, "Dockerfile").exists()) {
            results.add(result("DOCKER", 0.99, "Dockerfile", "Custom Dockerfile found"));
        }
        if (new File(root, "docker-compose.yml").exists() || new File(root, "compose.yaml").exists()) {
            results.add(result("COMPOSE", 0.99, "docker-compose.yml / compose.yaml", "Docker Compose file found"));
        }

        // Healthcheck
        if (new File(root, "pom.xml").exists()) {
            results.add(result("HEALTHCHECK", 0.60, "pom.xml", "Spring Boot may include Actuator health endpoint"));
        }

        // Static assets
        if (new File(root, "public").isDirectory() || new File(root, "static").isDirectory()) {
            results.add(result("STATIC_ASSETS", 0.90, "public/ or static/", "Static asset directory found"));
        }

        return results;
    }

    private DetectorResultV5 result(String capability, double confidence, String provenance, String evidence) {
        return DetectorResultV5.builder()
                .detectorName(name()).category("CAPABILITY").key(capability)
                .confidence(confidence).provenance(provenance).evidence(Arrays.asList(evidence))
                .build();
    }
}
