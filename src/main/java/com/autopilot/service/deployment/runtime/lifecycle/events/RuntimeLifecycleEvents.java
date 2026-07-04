package com.autopilot.service.deployment.runtime.lifecycle.events;

import com.autopilot.service.deployment.runtime.lifecycle.ApplicationRuntimeState;

public class RuntimeLifecycleEvents {

    public interface RuntimeLifecycleEvent {
        String getDeploymentId();
        long getTimestamp();
    }

    public static class ContainerCreatedEvent implements RuntimeLifecycleEvent {
        private final String deploymentId;
        private final long timestamp;
        private final String containerId;
        
        public ContainerCreatedEvent(String deploymentId, long timestamp, String containerId) {
            this.deploymentId = deploymentId;
            this.timestamp = timestamp;
            this.containerId = containerId;
        }
        @Override public String getDeploymentId() { return deploymentId; }
        @Override public long getTimestamp() { return timestamp; }
        public String getContainerId() { return containerId; }
    }

    public static class ContainerStartedEvent implements RuntimeLifecycleEvent {
        private final String deploymentId;
        private final long timestamp;
        private final String containerId;
        
        public ContainerStartedEvent(String deploymentId, long timestamp, String containerId) {
            this.deploymentId = deploymentId;
            this.timestamp = timestamp;
            this.containerId = containerId;
        }
        @Override public String getDeploymentId() { return deploymentId; }
        @Override public long getTimestamp() { return timestamp; }
        public String getContainerId() { return containerId; }
    }

    public static class PortBoundEvent implements RuntimeLifecycleEvent {
        private final String deploymentId;
        private final long timestamp;
        private final int port;
        
        public PortBoundEvent(String deploymentId, long timestamp, int port) {
            this.deploymentId = deploymentId;
            this.timestamp = timestamp;
            this.port = port;
        }
        @Override public String getDeploymentId() { return deploymentId; }
        @Override public long getTimestamp() { return timestamp; }
        public int getPort() { return port; }
    }

    public static class ReadinessConfirmedEvent implements RuntimeLifecycleEvent {
        private final String deploymentId;
        private final long timestamp;
        
        public ReadinessConfirmedEvent(String deploymentId, long timestamp) {
            this.deploymentId = deploymentId;
            this.timestamp = timestamp;
        }
        @Override public String getDeploymentId() { return deploymentId; }
        @Override public long getTimestamp() { return timestamp; }
    }

    public static class HealthConfirmedEvent implements RuntimeLifecycleEvent {
        private final String deploymentId;
        private final long timestamp;
        private final int statusCode;
        
        public HealthConfirmedEvent(String deploymentId, long timestamp, int statusCode) {
            this.deploymentId = deploymentId;
            this.timestamp = timestamp;
            this.statusCode = statusCode;
        }
        @Override public String getDeploymentId() { return deploymentId; }
        @Override public long getTimestamp() { return timestamp; }
        public int getStatusCode() { return statusCode; }
    }

    public static class RuntimeFailureEvent implements RuntimeLifecycleEvent {
        private final String deploymentId;
        private final long timestamp;
        private final ApplicationRuntimeState failedState;
        private final String reason;
        
        public RuntimeFailureEvent(String deploymentId, long timestamp, ApplicationRuntimeState failedState, String reason) {
            this.deploymentId = deploymentId;
            this.timestamp = timestamp;
            this.failedState = failedState;
            this.reason = reason;
        }
        @Override public String getDeploymentId() { return deploymentId; }
        @Override public long getTimestamp() { return timestamp; }
        public ApplicationRuntimeState getFailedState() { return failedState; }
        public String getReason() { return reason; }
    }
}
