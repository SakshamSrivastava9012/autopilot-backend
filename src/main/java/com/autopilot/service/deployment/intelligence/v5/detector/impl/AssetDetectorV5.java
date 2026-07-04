package com.autopilot.service.deployment.intelligence.v5.detector.impl;

import com.autopilot.service.deployment.intelligence.v5.detector.DetectorResultV5;
import com.autopilot.service.deployment.intelligence.v5.detector.RepositoryDetector;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Discovers static asset directories that need to be served at runtime.
 * Never rewrites or patches any assets.
 */
@Component
public class AssetDetectorV5 implements RepositoryDetector {
    @Override public String name() { return "AssetDetector"; }
    @Override public String version() { return "5.0.0"; }

    private static final String[] ASSET_DIRS = {
        "public", "static", "assets", "dist", "build", "out",
        "_next/static", "resources/static", "resources/public",
        "wwwroot", "web", "www"
    };

    @Override
    public List<DetectorResultV5> detect(String repositoryPath) {
        List<DetectorResultV5> results = new ArrayList<>();
        File root = new File(repositoryPath);

        for (String dir : ASSET_DIRS) {
            File candidate = new File(root, dir);
            if (candidate.isDirectory()) {
                results.add(DetectorResultV5.builder()
                        .detectorName(name()).category("ASSET").key(dir)
                        .value(dir + "/").confidence(0.90).provenance(dir + "/")
                        .evidence(Arrays.asList("Static asset directory '" + dir + "/' exists"))
                        .build());
            }
        }

        return results;
    }
}
