package com.autopilot.service.deployment.runtime.configuration;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

public class ConfigurationReports {

    @Data @Builder public static class ConfigurationNegotiationReport {
        private String negotiatedStyle;
        private int confidence;
        private List<String> evidenceSources;
        private String rationale;
    }

    @Data @Builder public static class ConfigurationConflictReport {
        private boolean hasConflicts;
        private List<String> duplicateDatasources;
        private List<String> overridingCredentials;
        private String resolutionApplied;
    }

    @Data @Builder public static class EnvironmentValidationReport {
        private boolean isValid;
        private List<String> missingRequiredVariables;
        private List<String> invalidUris;
        private List<String> expiredSecrets;
    }

    @Data @Builder public static class EnvironmentInjectionReport {
        private int variablesInjected;
        private List<String> variablesOmitted;
        private List<String> variablesOverridden;
        private boolean exactlyMatchesContract;
    }

    @Data @Builder public static class ConfigurationResolutionTimeline {
        private long discoveryDurationMs;
        private long negotiationDurationMs;
        private long validationDurationMs;
        private long injectionDurationMs;
    }
}
