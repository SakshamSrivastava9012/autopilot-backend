package com.autopilot.service.deployment.intelligence.v5.detector.impl;

import com.autopilot.service.deployment.intelligence.v5.detector.DetectorResultV5;
import com.autopilot.service.deployment.intelligence.v5.detector.RepositoryDetector;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Detects programming languages by examining build files, lock files, and source directories.
 */
@Component
public class LanguageDetectorV5 implements RepositoryDetector {
    @Override public String name() { return "LanguageDetector"; }
    @Override public String version() { return "5.0.0"; }

    @Override
    public List<DetectorResultV5> detect(String repositoryPath) {
        List<DetectorResultV5> results = new ArrayList<>();
        File root = new File(repositoryPath);

        if (new File(root, "pom.xml").exists() || new File(root, "build.gradle").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("LANGUAGE").key("Java")
                    .confidence(0.99).provenance("pom.xml / build.gradle").evidence(Arrays.asList("JVM build file found")).build());
        }
        if (new File(root, "package.json").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("LANGUAGE").key("JavaScript")
                    .confidence(0.95).provenance("package.json").evidence(Arrays.asList("Node.js project file found")).build());
        }
        if (new File(root, "requirements.txt").exists() || new File(root, "Pipfile").exists() || new File(root, "pyproject.toml").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("LANGUAGE").key("Python")
                    .confidence(0.95).provenance("requirements.txt / Pipfile").evidence(Arrays.asList("Python dependency file found")).build());
        }
        if (new File(root, "go.mod").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("LANGUAGE").key("Go")
                    .confidence(0.99).provenance("go.mod").evidence(Arrays.asList("Go module file found")).build());
        }
        if (new File(root, "Cargo.toml").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("LANGUAGE").key("Rust")
                    .confidence(0.99).provenance("Cargo.toml").evidence(Arrays.asList("Rust cargo manifest found")).build());
        }
        if (new File(root, "composer.json").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("LANGUAGE").key("PHP")
                    .confidence(0.95).provenance("composer.json").evidence(Arrays.asList("PHP Composer manifest found")).build());
        }
        if (new File(root, "Gemfile").exists()) {
            results.add(DetectorResultV5.builder().detectorName(name()).category("LANGUAGE").key("Ruby")
                    .confidence(0.95).provenance("Gemfile").evidence(Arrays.asList("Ruby Gemfile found")).build());
        }

        return results;
    }
}
