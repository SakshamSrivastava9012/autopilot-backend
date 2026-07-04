package com.autopilot.service.deployment.runtime.dependency;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

public class DependencyReports {

    @Data
    @Builder
    public static class DependencyReport {
        private String dependencyType;
        private String state;
        private String provider;
    }

    @Data
    @Builder
    public static class ProvisioningReport {
        private String dependencyName;
        private boolean success;
        private String message;
        private String provisionedProvider;
    }

    @Data
    @Builder
    public static class ValidationReport {
        private String dependencyName;
        private boolean dnsValid;
        private boolean tcpValid;
        private boolean authValid;
        private boolean isReady;
    }

    @Data
    @Builder
    public static class NegotiationReport {
        private String dependencyName;
        private String detectedProvider;
        private String negotiatedProvider;
        private String reason;
    }

    @Data
    @Builder
    public static class FallbackReport {
        private String dependencyName;
        private boolean fallbackTriggered;
        private String originalProvider;
        private String fallbackProvider;
        private String fallbackReason;
    }

    @Data
    @Builder
    public static class CredentialValidationReport {
        private String failureType; // e.g. DATABASE_AUTHENTICATION_FAILED, DATABASE_NOT_FOUND, etc.
        private String expectedCredentials; // Redacted or description
        private String actualNegotiatedCredentialsRedacted;
        private String provider;
        private String validationStep;
        private String rootCause;
        private String suggestedFix;
        private boolean success;
    }
}
