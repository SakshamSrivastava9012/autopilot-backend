package com.autopilot.service.deployment.runtime.dependency;

public class RuntimeConfigurationMismatchException extends RuntimeException {
    public RuntimeConfigurationMismatchException(String message) {
        super(message);
    }
}
