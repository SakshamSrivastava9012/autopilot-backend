package com.autopilot.service.deployment.v5.runtime.execution.tracker;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-Time Deployment Progress Tracker.
 * Tracks progress stage, percentage, estimated remaining time, current operation, and output timestamps.
 */
@Service
public class DeploymentProgressTracker {

    private static class StageState {
        DeploymentStage stage = DeploymentStage.CLONE;
        int percentage = 0;
        long startTime = System.currentTimeMillis();
        long lastOutputTime = System.currentTimeMillis();
        String currentOperation = "Initializing execution...";
    }

    private final Map<String, StageState> sessionStates = new ConcurrentHashMap<>();

    public void updateProgress(String sessionId, DeploymentStage stage, int percentage, String operation) {
        if (sessionId == null) return;

        StageState state = sessionStates.computeIfAbsent(sessionId, k -> new StageState());
        state.stage = stage != null ? stage : state.stage;
        state.percentage = percentage >= 0 ? percentage : state.stage.getDefaultProgressPercentage();
        state.currentOperation = operation != null ? operation : state.currentOperation;
        state.lastOutputTime = System.currentTimeMillis();
    }

    public void recordActivity(String sessionId) {
        if (sessionId == null) return;
        StageState state = sessionStates.get(sessionId);
        if (state != null) {
            state.lastOutputTime = System.currentTimeMillis();
        }
    }

    public ProgressSnapshot getSnapshot(String sessionId) {
        StageState state = sessionStates.get(sessionId);
        if (state == null) {
            return ProgressSnapshot.builder()
                    .sessionId(sessionId)
                    .stage(DeploymentStage.CLONE)
                    .percentage(0)
                    .estimatedRemainingTimeMs(120000)
                    .currentOperation("Pending...")
                    .lastOutputTimestamp(System.currentTimeMillis())
                    .durationMs(0)
                    .build();
        }

        long now = System.currentTimeMillis();
        long elapsed = now - state.startTime;
        long estRemaining = state.percentage > 0 ? (elapsed * (100 - state.percentage)) / state.percentage : 120000;

        return ProgressSnapshot.builder()
                .sessionId(sessionId)
                .stage(state.stage)
                .percentage(state.percentage)
                .estimatedRemainingTimeMs(estRemaining)
                .currentOperation(state.currentOperation)
                .lastOutputTimestamp(state.lastOutputTime)
                .durationMs(elapsed)
                .build();
    }

    public void clear(String sessionId) {
        sessionStates.remove(sessionId);
    }
}
