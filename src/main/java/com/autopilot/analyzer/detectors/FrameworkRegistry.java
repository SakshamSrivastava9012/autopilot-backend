package com.autopilot.analyzer.detectors;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class FrameworkRegistry {
    private final List<FrameworkDetector> detectors = new ArrayList<>();

    public FrameworkRegistry(List<FrameworkDetector> autowiredDetectors) {
        // Sort detectors programmatically:
        // 1. UserDockerfileDetector always first
        // 2. GenericFallbackDetector always last
        // 3. Others in between
        List<FrameworkDetector> sorted = new ArrayList<>(autowiredDetectors);
        sorted.sort((d1, d2) -> {
            if (d1 instanceof UserDockerfileDetector) return -1;
            if (d2 instanceof UserDockerfileDetector) return 1;
            if (d1 instanceof GenericFallbackDetector) return 1;
            if (d2 instanceof GenericFallbackDetector) return -1;
            return d1.getClass().getSimpleName().compareTo(d2.getClass().getSimpleName());
        });
        this.detectors.addAll(sorted);
    }

    public void registerDetector(FrameworkDetector detector) {
        // Insert at index 1 to be behind UserDockerfileDetector but before fallbacks
        if (detectors.isEmpty()) {
            detectors.add(detector);
        } else if (detector instanceof UserDockerfileDetector) {
            detectors.add(0, detector);
        } else {
            int insertIndex = 0;
            for (int i = 0; i < detectors.size(); i++) {
                if (detectors.get(i) instanceof GenericFallbackDetector) {
                    insertIndex = i;
                    break;
                }
            }
            detectors.add(insertIndex, detector);
        }
    }

    public List<FrameworkDetector> getDetectors() {
        return detectors;
    }

    public FrameworkDetector findMatchingDetector(Path workspace, List<String> files) {
        return detectors.stream()
                .filter(d -> d.matches(workspace, files))
                .findFirst()
                .orElse(detectors.get(detectors.size() - 1)); // fallback
    }
}
