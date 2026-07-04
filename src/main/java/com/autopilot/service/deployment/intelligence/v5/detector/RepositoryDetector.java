package com.autopilot.service.deployment.intelligence.v5.detector;

import java.util.List;

/**
 * Contract for all V5 repository detectors.
 * Detectors are independent — they must never call each other.
 * Their sole job is to observe filesystem artifacts and return evidence-backed results.
 *
 * @since V5
 */
public interface RepositoryDetector {

    /** Unique name for this detector (e.g. "LanguageDetector", "CapabilityDetector"). */
    String name();

    /** Semantic version of this detector for RepositoryModel provenance tracking. */
    String version();

    /**
     * Scan the repository at the given path and return discoveries.
     * Must be idempotent. Must never mutate the filesystem.
     *
     * @param repositoryPath Absolute path to the cloned repository.
     * @return Evidence-backed detector results.
     */
    List<DetectorResultV5> detect(String repositoryPath);
}
