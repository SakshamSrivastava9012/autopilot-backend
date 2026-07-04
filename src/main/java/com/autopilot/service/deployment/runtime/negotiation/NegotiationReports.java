package com.autopilot.service.deployment.runtime.negotiation;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

public class NegotiationReports {

    @Data @Builder public static class StartupCrashReport {
        private int exitCode;
        private boolean oomKilled;
        private int restartCount;
        private String signal;
        private List<String> containerLogs;
        private String lastState;
    }

    @Data @Builder public static class EnvironmentValidationReport {
        private boolean isValid;
        private List<String> missingVars;
        private List<String> duplicateVars;
        private List<String> conflictingVars;
        private String datasourceMismatch;
    }

    @Data @Builder public static class PortDiscoveryReport {
        private List<Integer> discoveredPorts;
        private String discoveryMethod; // e.g. "docker inspect", "ss", "fallback"
        private boolean isTcpListening;
    }

    @Data @Builder public static class ContainerLifecycleReport {
        private String containerId;
        private String status;
        private String errorReason;
        private boolean isRunning;
    }

    @Data @Builder public static class RuntimeStartupReport {
        private StartupState currentState;
        private boolean isStalled;
        private boolean isSuccessful;
        private long durationMs;
        private StartupCrashReport crashReport;
        private PortDiscoveryReport portReport;
        private ContainerLifecycleReport lifecycleReport;
        private EnvironmentValidationReport envReport;
    }
}
