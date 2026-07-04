package com.autopilot.service.deployment.runtime.dependency;

public record RuntimeContainerDescriptor(
    String applicationContainerName,
    String databaseContainerName,
    String networkName,
    Integer applicationPort,
    Integer databasePort
) {}
