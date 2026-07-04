package com.autopilot.service.deployment.runtime.dependency;

public class WaitEngine {
    private final RuntimeDatabaseConfiguration config;
    private final RuntimeContainerDescriptor descriptor;

    public WaitEngine(RuntimeDatabaseConfiguration config) {
        this.config = config;
        this.descriptor = null;
    }

    public WaitEngine(RuntimeContainerDescriptor descriptor) {
        this.config = null;
        this.descriptor = descriptor;
    }

    public void waitForReady() {
        String container = (descriptor != null) ? descriptor.databaseContainerName() : (config != null ? config.containerName() : "unknown");
        System.out.println("⏳ WaitEngine waiting for container: " + container);
    }
}
