package com.autopilot.service.deployment.runtime.health;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

public class HealthReports {

    @Data
    @Builder
    public static class HealthNegotiationReport {
        private String negotiatedStrategy;
        private String expectedStrategy;
        private String rationale;
        private Map<String, Object> detectionMetadata;
    }

    @Data
    @Builder
    public static class ReadinessReport {
        private HealthState state;
        private boolean isReady;
        private String evidence;
        private long durationMs;
    }

    @Data
    @Builder
    public static class LivenessReport {
        private HealthState state;
        private boolean isAlive;
        private String processId;
        private String evidence;
    }

    @Data
    @Builder
    public static class HealthFailureReport {
        private String rootCause;
        private String evidence;
        private String negotiatedStrategy;
        private String expectedStrategy;
        private String observedResult;
        private String recommendation;
        private HealthState terminalState;
    }
}
