package com.autopilot.service.deployment.strategies;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.FrameworkType;
import org.springframework.stereotype.Component;

public class HealthCheckStrategies {

    @Component
    public static class SpringBootHealthCheckStrategy implements HealthCheckStrategy {
        @Override
        public boolean supports(FrameworkType frameworkType) {
            return frameworkType == FrameworkType.SPRING_BOOT;
        }

        @Override
        public String getHealthCheckEndpoint(FrameworkMetadata metadata) {
            return "/actuator/health";
        }
    }

    @Component
    public static class QuarkusHealthCheckStrategy implements HealthCheckStrategy {
        @Override
        public boolean supports(FrameworkType frameworkType) {
            return frameworkType == FrameworkType.QUARKUS;
        }

        @Override
        public String getHealthCheckEndpoint(FrameworkMetadata metadata) {
            return "/q/health";
        }
    }

    @Component
    public static class DefaultHealthCheckStrategy implements HealthCheckStrategy {
        @Override
        public boolean supports(FrameworkType frameworkType) {
            return true; // fallback
        }

        @Override
        public String getHealthCheckEndpoint(FrameworkMetadata metadata) {
            if (metadata.getHealthCheckPath() != null && !metadata.getHealthCheckPath().isEmpty()) {
                return metadata.getHealthCheckPath();
            }
            return "/";
        }
    }
}
