package com.autopilot.service.deployment.v5.inspection;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Reports produced by the inspection pipeline.
 *
 * @since V5.3
 */
public class InspectionReports {

    @Value @Builder
    public static class RuntimeInspectionReport {
        String imageId;
        String runtimeType;
        int portsDiscovered;
        String healthStrategy;
        boolean hasDockerHealthcheck;
        long inspectionDurationMs;
        List<String> warnings;
    }

    @Value @Builder
    public static class AssetDiscoveryReport {
        int totalAssets;
        int cacheableAssets;
        int basePathSensitiveAssets;
        List<String> staticRoots;
        List<String> warnings;
    }

    @Value @Builder
    public static class CompatibilityReport {
        boolean spaDetected;
        boolean ssrDetected;
        boolean websocketDetected;
        boolean oauthDetected;
        int capabilitiesDetected;
        List<String> warnings;
    }
}
