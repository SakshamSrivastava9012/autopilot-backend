package com.autopilot.service.deployment.intelligence;

import java.util.List;

/**
 * Interface for all intelligence detectors scanning the repository.
 * Detectors must not call each other. They operate independently and return DetectorResults.
 */
public interface RepositoryScanner {
    /**
     * Inspects the repository and returns discovered traits.
     * @param repositoryPath The absolute path to the repository on disk.
     * @return A list of discovery results with confidence and provenance.
     */
    List<DetectorResult> scan(String repositoryPath);
}
