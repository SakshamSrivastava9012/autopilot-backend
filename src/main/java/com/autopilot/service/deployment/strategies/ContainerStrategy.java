package com.autopilot.service.deployment.strategies;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.FrameworkType;
import java.util.Map;

public interface ContainerStrategy {
    boolean supports(FrameworkType frameworkType);
    void populateEnvironment(FrameworkMetadata metadata, Map<String, String> envMap, String awsRegion);
}
