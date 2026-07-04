package com.autopilot.service.deployment.v5.runtime.execution.stream;

import com.autopilot.service.log.DeploymentLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Universal Log Streaming Engine.
 * Responsibilities:
 * - Stream SSM stdout/stderr in real time
 * - Stream Docker logs
 * - Stream cloud-init, Spring Boot, and Frontend logs
 * - Continuously publish events to subscribed consumers and forward to DeploymentLogService for SSE frontend streaming.
 * - Designed to support future providers (AWS SSM, Kubernetes, Docker Local, Azure, GCP)
 */
@Service
public class LogStreamingEngine {

    private final Map<String, List<ExecutionLogStreamEvent>> sessionLogs = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<ExecutionLogStreamEvent>>> sessionListeners = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private DeploymentLogService deploymentLogService;

    public void publishLog(String sessionId, ExecutionLogStreamEvent.LogSource source, String message, String level) {
        if (sessionId == null || message == null) return;

        ExecutionLogStreamEvent logEvent = ExecutionLogStreamEvent.builder()
                .logId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .source(source)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .streamLevel(level != null ? level : "INFO")
                .build();

        sessionLogs.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(logEvent);

        // Forward to DeploymentLogService so logs stream to Frontend via Redis Pub/Sub + SSE
        if (deploymentLogService != null) {
            try {
                String stageName = source != null ? source.name() : "EXECUTION";
                if ("ERROR".equalsIgnoreCase(level)) {
                    deploymentLogService.error(sessionId, stageName, message);
                } else if ("WARN".equalsIgnoreCase(level)) {
                    deploymentLogService.warn(sessionId, stageName, message);
                } else {
                    deploymentLogService.info(sessionId, stageName, message);
                }
            } catch (Exception ignored) {}
        }

        List<Consumer<ExecutionLogStreamEvent>> listeners = sessionListeners.get(sessionId);
        if (listeners != null) {
            for (Consumer<ExecutionLogStreamEvent> listener : listeners) {
                try {
                    listener.accept(logEvent);
                } catch (Exception ignored) {}
            }
        }
    }

    public void subscribe(String sessionId, Consumer<ExecutionLogStreamEvent> consumer) {
        if (sessionId == null || consumer == null) return;
        sessionListeners.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(consumer);
    }

    public List<ExecutionLogStreamEvent> getLogsForSession(String sessionId) {
        return sessionLogs.getOrDefault(sessionId, Collections.emptyList());
    }

    public void clearSession(String sessionId) {
        sessionLogs.remove(sessionId);
        sessionListeners.remove(sessionId);
    }
}
