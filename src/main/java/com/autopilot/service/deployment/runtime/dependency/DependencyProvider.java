package com.autopilot.service.deployment.runtime.dependency;

import java.util.List;

public interface DependencyProvider {
    ContainerId start();
    StartupResult waitUntilReady();
    HealthResult health();
    ConnectionInfo connectionInfo();
    List<String> cleanup();
    List<String> diagnostics();
}
