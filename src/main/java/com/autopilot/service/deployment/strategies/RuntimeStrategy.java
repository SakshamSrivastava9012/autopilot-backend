package com.autopilot.service.deployment.strategies;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.RuntimeType;
import java.util.List;

public interface RuntimeStrategy {
    boolean supports(RuntimeType runtimeType);
    String generateDockerfile(FrameworkMetadata metadata);

    default int containerPort(FrameworkMetadata metadata) {
        return metadata.getPort() != 0 ? metadata.getPort() : 8080;
    }

    default String healthPath(FrameworkMetadata metadata) {
        return metadata.getHealthCheckPath() != null ? metadata.getHealthCheckPath() : "/";
    }

    default String protocol(FrameworkMetadata metadata) {
        return "HTTP";
    }

    default List<Integer> expectedStatusCodes(FrameworkMetadata metadata) {
        return List.of(200, 204, 301, 302, 404);
    }

    default int startupTimeout(FrameworkMetadata metadata) {
        return 60;
    }

    default int retryPolicy(FrameworkMetadata metadata) {
        return 20;
    }
}

