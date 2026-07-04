package com.autopilot.analyzer.detectors;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.List;

@Component
public class FrameworkDetectorFactory {
    private final FrameworkRegistry registry;

    public FrameworkDetectorFactory(FrameworkRegistry registry) {
        this.registry = registry;
    }

    public FrameworkDetector getDetector(Path workspace, List<String> files) {
        return registry.findMatchingDetector(workspace, files);
    }
}
