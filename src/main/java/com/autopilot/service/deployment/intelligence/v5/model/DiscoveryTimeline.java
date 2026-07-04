package com.autopilot.service.deployment.intelligence.v5.model;

import lombok.Builder;
import lombok.Value;
import java.util.Map;

/**
 * Records precise timing for each phase of repository discovery.
 * Used for diagnostics and performance regression detection.
 *
 * @since V5
 */
@Value
@Builder
public class DiscoveryTimeline {
    long totalDurationMs;
    Map<String, Long> detectorDurations; // detector name -> duration in ms
    long scanStartEpoch;
    long scanEndEpoch;
}
