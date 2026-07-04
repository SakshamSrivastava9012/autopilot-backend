package com.autopilot.service.deployment.v5.runtime.execution.metrics;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Deployment Metrics Collector.
 * Collects precise execution durations across clone, detection, negotiation, build, push, infrastructure, docker pull, startup, verification, and total deployment time.
 */
@Service
public class DeploymentMetricsCollector {

    private static class MetricState {
        long startTime = System.currentTimeMillis();
        long repoCloneTimeMs;
        long detectionTimeMs;
        long negotiationTimeMs;
        long buildTimeMs;
        long pushTimeMs;
        long terraformTimeMs;
        long infrastructureTimeMs;
        long dockerPullTimeMs;
        long containerStartupTimeMs;
        long springBootStartupTimeMs;
        long frontendStartupTimeMs;
        long verificationTimeMs;
    }

    private final Map<String, MetricState> metricsMap = new ConcurrentHashMap<>();

    public void startSession(String sessionId) {
        if (sessionId == null) return;
        MetricState state = new MetricState();
        state.startTime = System.currentTimeMillis();
        metricsMap.put(sessionId, state);
    }

    public void recordMetric(String sessionId, String metricKey, long durationMs) {
        if (sessionId == null || metricKey == null) return;
        MetricState state = metricsMap.computeIfAbsent(sessionId, k -> new MetricState());

        switch (metricKey.toUpperCase()) {
            case "CLONE" -> state.repoCloneTimeMs = durationMs;
            case "DETECTION" -> state.detectionTimeMs = durationMs;
            case "NEGOTIATION" -> state.negotiationTimeMs = durationMs;
            case "BUILD" -> state.buildTimeMs = durationMs;
            case "PUSH" -> state.pushTimeMs = durationMs;
            case "TERRAFORM" -> state.terraformTimeMs = durationMs;
            case "INFRASTRUCTURE" -> state.infrastructureTimeMs = durationMs;
            case "DOCKER_PULL" -> state.dockerPullTimeMs = durationMs;
            case "CONTAINER_STARTUP" -> state.containerStartupTimeMs = durationMs;
            case "SPRING_BOOT_STARTUP" -> state.springBootStartupTimeMs = durationMs;
            case "FRONTEND_STARTUP" -> state.frontendStartupTimeMs = durationMs;
            case "VERIFICATION" -> state.verificationTimeMs = durationMs;
        }
    }

    public ExecutionMetricsSnapshot getSnapshot(String sessionId) {
        MetricState state = metricsMap.get(sessionId);
        if (state == null) {
            return ExecutionMetricsSnapshot.builder()
                    .sessionId(sessionId)
                    .totalDeploymentTimeMs(0)
                    .build();
        }

        long total = System.currentTimeMillis() - state.startTime;
        return ExecutionMetricsSnapshot.builder()
                .sessionId(sessionId)
                .repoCloneTimeMs(state.repoCloneTimeMs)
                .detectionTimeMs(state.detectionTimeMs)
                .negotiationTimeMs(state.negotiationTimeMs)
                .buildTimeMs(state.buildTimeMs)
                .pushTimeMs(state.pushTimeMs)
                .terraformTimeMs(state.terraformTimeMs)
                .infrastructureTimeMs(state.infrastructureTimeMs)
                .dockerPullTimeMs(state.dockerPullTimeMs)
                .containerStartupTimeMs(state.containerStartupTimeMs)
                .springBootStartupTimeMs(state.springBootStartupTimeMs)
                .frontendStartupTimeMs(state.frontendStartupTimeMs)
                .verificationTimeMs(state.verificationTimeMs)
                .totalDeploymentTimeMs(total)
                .build();
    }

    public void clear(String sessionId) {
        metricsMap.remove(sessionId);
    }
}
