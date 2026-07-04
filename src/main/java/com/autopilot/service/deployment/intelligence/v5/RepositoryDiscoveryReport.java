package com.autopilot.service.deployment.intelligence.v5;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete discovery report produced by the Repository Intelligence Engine V5.
 * Exposes all analysis metadata for the Deployrix dashboard and downstream subsystems.
 *
 * @since V5 — ADR-004
 */
@Value
@Builder
public class RepositoryDiscoveryReport {
    Set<String> languages;
    Set<String> frameworks;
    Set<String> capabilities;
    int serviceCount;
    int dependencyCount;
    int assetDirectoryCount;
    int routeCount;
    int secretCount;
    int warningCount;
    double overallConfidence;
    long discoveryDurationMs;
    Map<String, Long> detectorTimings;
    List<String> warnings;
}
