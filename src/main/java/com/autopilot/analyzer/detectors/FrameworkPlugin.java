package com.autopilot.analyzer.detectors;

import com.autopilot.analyzer.model.ServiceConfig;
import java.util.List;

public interface FrameworkPlugin {

    List<ServiceConfig> detect(List<String> files);

    default boolean supports(String language, String framework) {
        return false;
    }

    default String generateDockerfileFallback(ServiceConfig service) {
        return null;
    }
}