package com.autopilot.service.deployment.v5.runtime.execution.timeline;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Deployment Timeline Engine.
 * Records phase start/end times, durations, warnings, retries, events, and logs per deployment session.
 */
@Service
public class DeploymentTimeline {

    private final Map<String, List<ExecutionTimelinePhase>> sessionTimelines = new ConcurrentHashMap<>();

    public void startPhase(String sessionId, String phaseName) {
        if (sessionId == null || phaseName == null) return;
        List<ExecutionTimelinePhase> phases = sessionTimelines.computeIfAbsent(sessionId, k -> new ArrayList<>());

        // Close previous running phase if any
        phases.stream()
                .filter(p -> "RUNNING".equals(p.getStatus()))
                .forEach(p -> {
                    p.setEndTime(System.currentTimeMillis());
                    p.setDurationMs(p.getEndTime() - p.getStartTime());
                    p.setStatus("COMPLETED");
                });

        ExecutionTimelinePhase phase = ExecutionTimelinePhase.builder()
                .phaseName(phaseName)
                .startTime(System.currentTimeMillis())
                .status("RUNNING")
                .warnings(new ArrayList<>())
                .events(new ArrayList<>())
                .logs(new ArrayList<>())
                .build();

        phases.add(phase);
    }

    public void addEvent(String sessionId, String phaseName, String eventMessage) {
        ExecutionTimelinePhase phase = getActiveOrNamedPhase(sessionId, phaseName);
        if (phase != null) {
            phase.getEvents().add(eventMessage);
        }
    }

    public void addWarning(String sessionId, String phaseName, String warning) {
        ExecutionTimelinePhase phase = getActiveOrNamedPhase(sessionId, phaseName);
        if (phase != null) {
            phase.getWarnings().add(warning);
        }
    }

    public void completePhase(String sessionId, String phaseName, boolean success) {
        ExecutionTimelinePhase phase = getActiveOrNamedPhase(sessionId, phaseName);
        if (phase != null) {
            phase.setEndTime(System.currentTimeMillis());
            phase.setDurationMs(phase.getEndTime() - phase.getStartTime());
            phase.setStatus(success ? "COMPLETED" : "FAILED");
        }
    }

    public List<ExecutionTimelinePhase> getTimelineForSession(String sessionId) {
        return sessionTimelines.getOrDefault(sessionId, Collections.emptyList());
    }

    private ExecutionTimelinePhase getActiveOrNamedPhase(String sessionId, String phaseName) {
        List<ExecutionTimelinePhase> phases = sessionTimelines.get(sessionId);
        if (phases == null || phases.isEmpty()) return null;

        if (phaseName != null) {
            for (int i = phases.size() - 1; i >= 0; i--) {
                if (phaseName.equalsIgnoreCase(phases.get(i).getPhaseName())) {
                    return phases.get(i);
                }
            }
        }
        return phases.get(phases.size() - 1);
    }

    public void clear(String sessionId) {
        sessionTimelines.remove(sessionId);
    }
}
