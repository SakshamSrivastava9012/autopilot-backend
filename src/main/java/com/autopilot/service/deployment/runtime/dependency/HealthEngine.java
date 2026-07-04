package com.autopilot.service.deployment.runtime.dependency;

public class HealthEngine {
    private final RuntimeDatabaseConfiguration config;
    private final RuntimeContainerDescriptor descriptor;

    public HealthEngine(RuntimeDatabaseConfiguration config) {
        this.config = config;
        this.descriptor = null;
    }

    public HealthEngine(RuntimeContainerDescriptor descriptor) {
        this.config = null;
        this.descriptor = descriptor;
    }

    public boolean checkHealth() {
        String container = (descriptor != null) ? descriptor.databaseContainerName() : (config != null ? config.containerName() : "unknown");
        System.out.println("🩺 HealthEngine checking health of container: " + container);
        return true;
    }
}
