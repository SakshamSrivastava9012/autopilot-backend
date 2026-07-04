package com.autopilot.service.deployment.runtime.dependency;

public record RuntimeDatabaseConfiguration(
    String databaseName,
    String username,
    String password,
    String rootPassword,
    int port,
    String containerName
){}
