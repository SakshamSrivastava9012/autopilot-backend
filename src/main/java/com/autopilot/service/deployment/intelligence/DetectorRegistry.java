package com.autopilot.service.deployment.intelligence;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Global registry for Repository Scanners (Detectors).
 * New languages and frameworks can be supported by registering plugins here.
 */
@Service
public class DetectorRegistry {
    private final List<RepositoryScanner> scanners = new ArrayList<>();

    public void register(RepositoryScanner scanner) {
        scanners.add(scanner);
    }

    public List<RepositoryScanner> getScanners() {
        return Collections.unmodifiableList(scanners);
    }
}
