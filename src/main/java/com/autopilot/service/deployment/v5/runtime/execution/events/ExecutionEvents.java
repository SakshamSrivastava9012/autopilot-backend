package com.autopilot.service.deployment.v5.runtime.execution.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

public class ExecutionEvents {

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeploymentStartedEvent extends ExecutionEvent {
        private String projectName;
        private String deploymentMode;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DockerPullStartedEvent extends ExecutionEvent {
        private String imageName;
        private int totalLayers;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DockerLayerDownloadedEvent extends ExecutionEvent {
        private String layerId;
        private long bytesDownloaded;
        private long totalBytes;
        private int downloadedLayers;
        private int totalLayers;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContainerCreatedEvent extends ExecutionEvent {
        private String containerId;
        private String containerName;
        private int port;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContainerHealthyEvent extends ExecutionEvent {
        private String containerName;
        private String healthEndpoint;
        private int statusCode;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SpringStartedEvent extends ExecutionEvent {
        private String activeProfile;
        private long startupDurationMs;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FrontendStartedEvent extends ExecutionEvent {
        private String framework;
        private int port;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VerificationStartedEvent extends ExecutionEvent {
        private int verificationModuleCount;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeploymentCompletedEvent extends ExecutionEvent {
        private long totalDurationMs;
        private boolean success;
    }
}
