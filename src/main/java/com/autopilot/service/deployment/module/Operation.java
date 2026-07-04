package com.autopilot.service.deployment.module;

/**
 * Represents a side-effect intent planned by a CompatibilityModule.
 * Implementations might include CreateContainerOperation, InjectEnvironmentOperation, etc.
 */
public interface Operation {
    String getOperationType();
    String getDescription();
}
