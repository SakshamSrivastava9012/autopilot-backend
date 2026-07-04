package com.autopilot.service.deployment.v5.build;

import com.autopilot.service.deployment.intelligence.v5.model.RepositoryModelV5;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Determines the optimal build strategy for a repository.
 * Pure decision logic — never executes builds.
 *
 * @since V5.3 — ADR-006
 */
@Service
public class BuildStrategyResolver {

    public BuildPlan resolve(RepositoryModelV5 model) {
        System.out.println("🔧 Build Strategy Resolver — Determining build strategy...");

        Set<String> capabilities = model.getCapabilities();
        Set<String> languages = model.getLanguages();
        Set<String> frameworks = model.getFrameworks();

        // Priority 1: Custom Dockerfile
        if (capabilities.contains("DOCKER")) {
            return plan("DOCKERFILE", null, null, null, 99,
                    "Custom Dockerfile found. Respecting user-defined build.");
        }

        // Priority 2: Framework-specific strategies
        if (frameworks.contains("Next.js")) {
            return plan("NEXTJS", "node:20-alpine", "npm run build", "npm start", 95, null);
        }
        if (frameworks.contains("Nuxt")) {
            return plan("NUXT", "node:20-alpine", "npm run build", "node .output/server/index.mjs", 95, null);
        }
        if (frameworks.contains("Angular")) {
            return plan("ANGULAR", "node:20-alpine", "npm run build", null, 90, null);
        }
        if (frameworks.contains("Vite")) {
            return plan("VITE", "node:20-alpine", "npm run build", null, 90, null);
        }
        if (frameworks.contains("Django")) {
            return plan("PYTHON", "python:3.12-slim", "pip install -r requirements.txt",
                    "gunicorn app.wsgi:application", 85, null);
        }
        if (frameworks.contains("Laravel")) {
            return plan("LARAVEL", "php:8.3-fpm", "composer install --no-dev",
                    "php artisan serve --host=0.0.0.0", 85, null);
        }
        if (frameworks.contains("Rails")) {
            return plan("RAILS", "ruby:3.3-slim", "bundle install",
                    "bundle exec rails server -b 0.0.0.0", 85, null);
        }

        // Priority 3: Language-based strategies
        if (languages.contains("Java")) {
            return plan("MAVEN", "eclipse-temurin:21-jre-alpine", "mvn package -DskipTests",
                    "java -jar target/*.jar", 90, null);
        }
        if (languages.contains("Go")) {
            return plan("GO", "golang:1.22-alpine", "go build -o /app/server .",
                    "/app/server", 90, null);
        }
        if (languages.contains("Rust")) {
            return plan("RUST", "rust:1.78-slim", "cargo build --release",
                    "./target/release/app", 90, null);
        }
        if (languages.contains("JavaScript")) {
            return plan("NPM", "node:20-alpine", "npm install && npm run build",
                    "npm start", 80, null);
        }
        if (languages.contains("Python")) {
            return plan("PYTHON", "python:3.12-slim", "pip install -r requirements.txt",
                    "python app.py", 75, null);
        }
        if (languages.contains("PHP")) {
            return plan("PHP", "php:8.3-fpm", "composer install", null, 70, null);
        }
        if (languages.contains("Ruby")) {
            return plan("RUBY", "ruby:3.3-slim", "bundle install", null, 70, null);
        }

        // Priority 4: Static site fallback
        if (capabilities.contains("STATIC_SITE") || capabilities.contains("STATIC_ASSETS")) {
            return plan("STATIC", "nginx:alpine", null, null, 65,
                    "No build system detected. Serving as static site.");
        }

        // Fallback
        List<String> warnings = new ArrayList<>();
        warnings.add("Could not determine build strategy. Attempting Buildpack.");
        return BuildPlan.builder()
                .strategy("BUILDPACK").confidence(40).warnings(warnings)
                .exposedPorts(Collections.emptyList()).buildArgs(Collections.emptyList())
                .labels(Collections.emptyMap()).build();
    }

    private BuildPlan plan(String strategy, String baseImage, String buildCmd, String startCmd,
                           int confidence, String warning) {
        List<String> warnings = warning != null ? Arrays.asList(warning) : Collections.emptyList();
        return BuildPlan.builder()
                .strategy(strategy).baseImage(baseImage).buildCommand(buildCmd).startCommand(startCmd)
                .usesCustomDockerfile("DOCKERFILE".equals(strategy))
                .confidence(confidence).warnings(warnings)
                .exposedPorts(Collections.emptyList()).buildArgs(Collections.emptyList())
                .labels(Collections.emptyMap()).build();
    }
}
