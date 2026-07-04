package com.autopilot.service.deployment.intelligence.v5.detector.impl;

import com.autopilot.service.deployment.intelligence.v5.detector.DetectorResultV5;
import com.autopilot.service.deployment.intelligence.v5.detector.RepositoryDetector;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Discovers service boundaries within monorepos, polyrepos, and multi-module projects.
 * Supports Nx, TurboRepo, Lerna, Maven multi-module, Gradle multi-project.
 */
@Component
public class ServiceDetectorV5 implements RepositoryDetector {
    @Override public String name() { return "ServiceDetector"; }
    @Override public String version() { return "5.0.0"; }

    private static final String[] SERVICE_DIRS = {
        "frontend", "backend", "api", "worker", "admin", "gateway",
        "web", "server", "client", "app", "service", "services"
    };

    @Override
    public List<DetectorResultV5> detect(String repositoryPath) {
        List<DetectorResultV5> results = new ArrayList<>();
        File root = new File(repositoryPath);

        // Check well-known monorepo service directories
        for (String dir : SERVICE_DIRS) {
            File candidate = new File(root, dir);
            if (candidate.isDirectory() && hasProjectFile(candidate)) {
                results.add(DetectorResultV5.builder()
                        .detectorName(name()).category("SERVICE").key(dir).value(dir + "/")
                        .confidence(0.85).provenance(dir + "/")
                        .evidence(Arrays.asList("Directory '" + dir + "/' contains a build/project file"))
                        .build());
            }
        }

        // Check apps/ and packages/ (Nx/TurboRepo/Lerna)
        checkSubdirectories(root, "apps", results);
        checkSubdirectories(root, "packages", results);

        return results;
    }

    private void checkSubdirectories(File root, String parent, List<DetectorResultV5> results) {
        File dir = new File(root, parent);
        if (dir.isDirectory()) {
            File[] children = dir.listFiles(File::isDirectory);
            if (children != null) {
                for (File child : children) {
                    if (hasProjectFile(child)) {
                        String path = parent + "/" + child.getName();
                        results.add(DetectorResultV5.builder()
                                .detectorName(name()).category("SERVICE").key(child.getName()).value(path + "/")
                                .confidence(0.90).provenance(path + "/")
                                .evidence(Arrays.asList("Monorepo service at '" + path + "/'"))
                                .build());
                    }
                }
            }
        }
    }

    private boolean hasProjectFile(File dir) {
        return new File(dir, "package.json").exists()
                || new File(dir, "pom.xml").exists()
                || new File(dir, "build.gradle").exists()
                || new File(dir, "Dockerfile").exists()
                || new File(dir, "requirements.txt").exists()
                || new File(dir, "go.mod").exists()
                || new File(dir, "Cargo.toml").exists()
                || new File(dir, "composer.json").exists();
    }
}
