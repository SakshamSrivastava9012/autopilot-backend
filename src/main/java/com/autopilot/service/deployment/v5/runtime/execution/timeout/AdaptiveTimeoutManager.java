package com.autopilot.service.deployment.v5.runtime.execution.timeout;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Intelligent Adaptive Timeout Manager.
 * Replaces hardcoded fixed timeouts with dynamic context-aware timeouts based on stage, image size, framework, and database migrations.
 */
@Service
public class AdaptiveTimeoutManager {

    public long calculateTimeoutMs(String stage, Map<String, Object> context) {
        if (stage == null) return 300_000L; // Default 5 minutes

        String stageUpper = stage.toUpperCase();

        if (stageUpper.contains("DOCKER_PULL") || stageUpper.contains("PULL")) {
            long imageSizeBytes = getLongContext(context, "imageSizeBytes", 500_000_000L);
            if (imageSizeBytes > 1_500_000_000L) { // > 1.5 GB
                return 15 * 60 * 1000L; // 15 mins for large images
            } else if (imageSizeBytes > 500_000_000L) { // > 500 MB
                return 7 * 60 * 1000L; // 7 mins
            } else {
                return 2 * 60 * 1000L; // 2 mins for small images
            }
        }

        if (stageUpper.contains("SPRING") || stageUpper.contains("BACKEND_STARTUP")) {
            boolean flywayOrLiquibaseDetected = getBooleanContext(context, "hasMigrations", false);
            if (flywayOrLiquibaseDetected) {
                return 180 * 1000L; // 180 sec for Flyway / Liquibase database migrations
            } else {
                return 60 * 1000L; // 60 sec standard Spring Boot
            }
        }

        if (stageUpper.contains("BUILD") || stageUpper.contains("IMAGE_BUILD")) {
            String framework = getStringContext(context, "framework", "GENERIC");
            if (framework.toUpperCase().contains("NEXT")) {
                return 300 * 1000L; // 300 sec for Next.js build
            } else if (framework.toUpperCase().contains("REACT") || framework.toUpperCase().contains("VITE")) {
                return 60 * 1000L; // 60 sec for static React/Vite
            } else {
                return 180 * 1000L; // 180 sec default build
            }
        }

        if (stageUpper.contains("TERRAFORM") || stageUpper.contains("INFRASTRUCTURE")) {
            return 600 * 1000L; // 10 minutes for cloud infra allocation
        }

        return 180 * 1000L; // 3 minutes default for generic operational stages
    }

    private long getLongContext(Map<String, Object> context, String key, long defaultValue) {
        if (context == null || !context.containsKey(key)) return defaultValue;
        Object val = context.get(key);
        if (val instanceof Number n) return n.longValue();
        return defaultValue;
    }

    private boolean getBooleanContext(Map<String, Object> context, String key, boolean defaultValue) {
        if (context == null || !context.containsKey(key)) return defaultValue;
        Object val = context.get(key);
        if (val instanceof Boolean b) return b;
        return defaultValue;
    }

    private String getStringContext(Map<String, Object> context, String key, String defaultValue) {
        if (context == null || !context.containsKey(key)) return defaultValue;
        Object val = context.get(key);
        if (val instanceof String s) return s;
        return defaultValue;
    }
}
