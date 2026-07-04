package com.autopilot.service.deployment.v5.runtime.execution.stall;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Deployment Stall Detector.
 * Replaces fixed timeouts (e.g. 600 seconds) by tracking active progress.
 *
 * Rules:
 * - If logs continue arriving -> Continue.
 * - If Docker pull progresses -> Continue.
 * - If Spring Boot prints logs -> Continue.
 * - Fails ONLY when no stdout, stderr, or progress events occur for the stall threshold (Default: 180s).
 */
@Service
public class DeploymentStallDetector {

    public static final long DEFAULT_STALL_THRESHOLD_MS = 180_000L; // 180 seconds

    private final Map<String, Long> lastActivityMap = new ConcurrentHashMap<>();
    private final Map<String, String> currentStageMap = new ConcurrentHashMap<>();

    public void recordActivity(String sessionId, String stage) {
        if (sessionId == null) return;
        lastActivityMap.put(sessionId, System.currentTimeMillis());
        if (stage != null) {
            currentStageMap.put(sessionId, stage);
        }
    }

    public StallReport checkStall(String sessionId) {
        return checkStall(sessionId, DEFAULT_STALL_THRESHOLD_MS);
    }

    public StallReport checkStall(String sessionId, long customThresholdMs) {
        if (sessionId == null) {
            return StallReport.builder().stalled(false).build();
        }

        Long lastActivity = lastActivityMap.get(sessionId);
        String stage = currentStageMap.getOrDefault(sessionId, "UNKNOWN");
        long now = System.currentTimeMillis();

        if (lastActivity == null) {
            lastActivityMap.put(sessionId, now);
            return StallReport.builder()
                    .sessionId(sessionId)
                    .stage(stage)
                    .lastProgressTimestamp(now)
                    .stallDurationMs(0)
                    .stalled(false)
                    .reason("Execution initialized")
                    .suggestedFix("None")
                    .build();
        }

        long stallDuration = now - lastActivity;
        boolean isStalled = stallDuration > customThresholdMs;

        String reason = isStalled
                ? "No stdout, stderr, Docker progress, or lifecycle events received for " + (stallDuration / 1000) + " seconds"
                : "Active progress observed within threshold (" + (stallDuration / 1000) + "s elapsed since last output)";

        String suggestedFix = isStalled
                ? "Check SSM agent connection, EC2 system resources, or target service logs for deadlock."
                : "None";

        return StallReport.builder()
                .sessionId(sessionId)
                .stage(stage)
                .lastProgressTimestamp(lastActivity)
                .stallDurationMs(stallDuration)
                .stalled(isStalled)
                .reason(reason)
                .suggestedFix(suggestedFix)
                .build();
    }

    public void clear(String sessionId) {
        lastActivityMap.remove(sessionId);
        currentStageMap.remove(sessionId);
    }
}
