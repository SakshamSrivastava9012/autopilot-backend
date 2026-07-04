package com.autopilot.service.deployment.runtime.verification;

import lombok.Builder;
import lombok.Data;
import java.util.List;

public class VerificationReports {

    @Data @Builder public static class RuntimeVerificationReport {
        private String moduleName;
        private VerificationSeverity severity;
        private boolean success;
        private String details;
    }

    @Data @Builder public static class BrowserVerificationReport {
        private int networkRequests;
        private int failedRequests;
        private int consoleErrors;
        private int jsExceptions;
        private boolean domLoaded;
    }

    @Data @Builder public static class AssetVerificationReport {
        private List<String> validatedAssets;
        private List<String> missingAssets;
        private boolean success;
    }

    @Data @Builder public static class APIVerificationReport {
        private String endpoint;
        private int statusCode;
        private boolean isAuthRedirect;
    }

    @Data @Builder public static class PerformanceReport {
        private long ttfbMs;
        private long fcpMs;
        private long lcpMs;
        private double cls;
        private int resourceCount;
    }

    @Data @Builder public static class AccessibilityReport {
        private int missingAltTags;
        private int contrastWarnings;
    }

    @Data @Builder public static class SecurityReport {
        private boolean authenticationExpected;
        private boolean corsWarnings;
    }

    @Data @Builder public static class ExternalDependencyReport {
        private List<String> unreachableCDNs;
    }

    @Data @Builder public static class SmokeTestReport {
        private boolean homePageLoads;
        private boolean healthEndpointPasses;
        private boolean databaseConnected;
    }

    @Data @Builder public static class DeploymentQualityReport {
        private boolean deploymentSuccess;
        private List<RuntimeVerificationReport> moduleReports;
        private BrowserVerificationReport browserReport;
        private AssetVerificationReport assetReport;
        private APIVerificationReport apiReport;
        private PerformanceReport performanceReport;
        private AccessibilityReport accessibilityReport;
        private SecurityReport securityReport;
        private ExternalDependencyReport externalDependencyReport;
        private SmokeTestReport smokeTestReport;
    }
}
