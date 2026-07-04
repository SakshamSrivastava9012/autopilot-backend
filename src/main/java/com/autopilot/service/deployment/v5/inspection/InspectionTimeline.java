package com.autopilot.service.deployment.v5.inspection;

import lombok.Builder;
import lombok.Value;
import java.util.Map;

/**
 * Immutable timeline recording each phase of runtime inspection.
 *
 * @since V5.3
 */
@Value
@Builder
public class InspectionTimeline {
    long imageCreatedAt;
    long containerCreatedAt;
    long inspectionStartedAt;
    long filesystemScannedAt;
    long runtimeAnalyzedAt;
    long inspectionCompletedAt;
    long totalDurationMs;
}
