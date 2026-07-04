package com.autopilot.service.deployment.intelligence.v5.detector;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for all V5 repository detectors.
 * New languages, frameworks, and capabilities are supported by registering detectors here.
 * Detectors are auto-discovered via Spring DI (all RepositoryDetector beans are injected).
 *
 * @since V5
 */
@Service
public class DetectorRegistryV5 {

    private final List<RepositoryDetector> detectors;

    public DetectorRegistryV5(List<RepositoryDetector> detectors) {
        this.detectors = detectors != null ? detectors : new ArrayList<>();
        System.out.println("🔌 V5 Detector Registry initialized with " + this.detectors.size() + " detectors.");
    }

    public List<RepositoryDetector> getDetectors() {
        return Collections.unmodifiableList(detectors);
    }

    public Map<String, String> getDetectorVersions() {
        Map<String, String> versions = new LinkedHashMap<>();
        for (RepositoryDetector detector : detectors) {
            versions.put(detector.name(), detector.version());
        }
        return Collections.unmodifiableMap(versions);
    }
}
