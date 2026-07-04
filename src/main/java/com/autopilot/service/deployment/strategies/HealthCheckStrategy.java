package com.autopilot.service.deployment.strategies;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.FrameworkType;

public interface HealthCheckStrategy {
    boolean supports(FrameworkType frameworkType);
    String getHealthCheckEndpoint(FrameworkMetadata metadata);
}
