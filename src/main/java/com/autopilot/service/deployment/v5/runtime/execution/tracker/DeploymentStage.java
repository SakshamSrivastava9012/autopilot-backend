package com.autopilot.service.deployment.v5.runtime.execution.tracker;

public enum DeploymentStage {
    CLONE("Repository Clone", 5),
    DETECTION("Service Intelligence Detection", 10),
    NEGOTIATION("Dependency & Config Negotiation", 20),
    PROVISIONING("Infrastructure & Dependency Provisioning", 35),
    DOCKER_PULL("Docker Image Pull", 50),
    IMAGE_BUILD("Docker Image Build", 65),
    CONTAINER_STARTUP("Container Startup", 75),
    DEPENDENCY_STARTUP("Runtime Dependency Startup", 85),
    HEALTH("Runtime Health Negotiation", 90),
    VERIFICATION("Universal Verification Platform", 95),
    COMPLETED("Deployment Finalized", 100);

    private final String displayName;
    private final int defaultProgressPercentage;

    DeploymentStage(String displayName, int defaultProgressPercentage) {
        this.displayName = displayName;
        this.defaultProgressPercentage = defaultProgressPercentage;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultProgressPercentage() {
        return defaultProgressPercentage;
    }
}
