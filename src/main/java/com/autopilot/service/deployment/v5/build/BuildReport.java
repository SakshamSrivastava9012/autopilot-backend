package com.autopilot.service.deployment.v5.build;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Immutable report of a build execution.
 *
 * @since V5.3
 */
@Value
@Builder
public class BuildReport {
    String serviceId;
    String strategy;
    boolean success;
    long durationMs;
    long imageSizeBytes;
    List<String> warnings;
    List<String> errors;
}
