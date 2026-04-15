package com.autopilot.controller;

import com.autopilot.entity.DeploymentLog;
import com.autopilot.service.log.DeploymentLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * SSE endpoint for real-time deployment log streaming.
 * <p>
 * Flow:
 *   1. Client connects to GET /deploy/{id}/logs/stream
 *   2. Server immediately replays all existing log entries (catch-up)
 *   3. Server subscribes to Redis pub/sub channel for that deployment
 *   4. New log entries are pushed in real-time as SSE events
 *   5. On pipeline completion, a "__COMPLETE__" sentinel closes the stream
 */
@Slf4j
@RestController
@RequestMapping("/deploy")
@RequiredArgsConstructor
public class DeploymentLogController {

    private final DeploymentLogService logService;
    private final RedisMessageListenerContainer listenerContainer;

    /**
     * SSE stream endpoint — long-lived connection for real-time logs.
     *
     * @param id  the deployment ID
     * @return SseEmitter that streams log entries as JSON events
     */
    @GetMapping(value = "/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable String id) {

        // 10 minutes timeout — deployments can take a while
        SseEmitter emitter = new SseEmitter(600_000L);

        // ── 1. Replay historical logs (catch-up) ────────────────────────
        try {
            List<DeploymentLog> history = logService.getLogsForDeployment(id);
            for (DeploymentLog entry : history) {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(entry, MediaType.APPLICATION_JSON));
            }
        } catch (IOException e) {
            log.error("Failed to send historical logs for deployment {}", id);
            emitter.completeWithError(e);
            return emitter;
        }

        // ── 2. Subscribe to Redis pub/sub for live updates ──────────────
        String channel = DeploymentLogService.channelFor(id);
        ChannelTopic topic = new ChannelTopic(channel);

        MessageListener listener = (message, pattern) -> {
            try {
                String payload = new String(message.getBody());

                // Sentinel: pipeline finished
                if ("__COMPLETE__".equals(payload)) {
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data("{\"status\":\"complete\"}"));
                    emitter.complete();
                    return;
                }

                // Normal log entry (JSON)
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(payload, MediaType.APPLICATION_JSON));

            } catch (IOException e) {
                log.debug("SSE client disconnected for deployment {}", id);
                emitter.completeWithError(e);
            } catch (IllegalStateException e) {
                // Emitter already completed/timed out
                log.debug("Emitter already completed for deployment {}", id);
            }
        };

        // Register the Redis listener
        listenerContainer.addMessageListener(listener, topic);

        // ── 3. Cleanup on disconnect / timeout / completion ─────────────
        Runnable cleanup = () -> {
            listenerContainer.removeMessageListener(listener, topic);
            log.debug("Cleaned up Redis listener for deployment {}", id);
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    /**
     * REST endpoint to fetch all logs for a deployment (non-streaming, for history).
     */
    @GetMapping("/{id}/logs")
    public List<DeploymentLog> getLogs(@PathVariable String id) {
        return logService.getLogsForDeployment(id);
    }
}
