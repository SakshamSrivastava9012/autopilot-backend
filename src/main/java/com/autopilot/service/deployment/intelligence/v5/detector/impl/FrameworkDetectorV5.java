package com.autopilot.service.deployment.intelligence.v5.detector.impl;

import com.autopilot.service.deployment.intelligence.v5.detector.DetectorResultV5;
import com.autopilot.service.deployment.intelligence.v5.detector.RepositoryDetector;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects frameworks (informational only — capabilities, not frameworks, drive deployment).
 */
@Component
public class FrameworkDetectorV5 implements RepositoryDetector {
    @Override public String name() { return "FrameworkDetector"; }
    @Override public String version() { return "5.0.0"; }

    @Override
    public List<DetectorResultV5> detect(String repositoryPath) {
        List<DetectorResultV5> results = new ArrayList<>();
        File root = new File(repositoryPath);

        if (new File(root, "next.config.js").exists() || new File(root, "next.config.mjs").exists() || new File(root, "next.config.ts").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("FRAMEWORK").key("Next.js")
                    .confidence(0.99).provenance("next.config.*").evidence(Arrays.asList("Next.js configuration file found")).build());
        }
        if (new File(root, "nuxt.config.ts").exists() || new File(root, "nuxt.config.js").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("FRAMEWORK").key("Nuxt")
                    .confidence(0.99).provenance("nuxt.config.*").evidence(Arrays.asList("Nuxt configuration file found")).build());
        }
        if (new File(root, "angular.json").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("FRAMEWORK").key("Angular")
                    .confidence(0.99).provenance("angular.json").evidence(Arrays.asList("Angular workspace config found")).build());
        }
        if (new File(root, "vite.config.ts").exists() || new File(root, "vite.config.js").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("FRAMEWORK").key("Vite")
                    .confidence(0.90).provenance("vite.config.*").evidence(Arrays.asList("Vite configuration file found")).build());
        }
        if (new File(root, "svelte.config.js").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("FRAMEWORK").key("Svelte")
                    .confidence(0.99).provenance("svelte.config.js").evidence(Arrays.asList("SvelteKit config found")).build());
        }
        if (new File(root, "manage.py").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("FRAMEWORK").key("Django")
                    .confidence(0.95).provenance("manage.py").evidence(Arrays.asList("Django management script found")).build());
        }
        if (new File(root, "artisan").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("FRAMEWORK").key("Laravel")
                    .confidence(0.95).provenance("artisan").evidence(Arrays.asList("Laravel artisan CLI found")).build());
        }
        if (new File(root, "config.ru").exists() || new File(root, "Rakefile").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("FRAMEWORK").key("Rails")
                    .confidence(0.90).provenance("config.ru / Rakefile").evidence(Arrays.asList("Rails project structure found")).build());
        }

        return results;
    }
}
