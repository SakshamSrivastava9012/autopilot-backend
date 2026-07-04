package com.autopilot.service.log;

import com.autopilot.entity.DeploymentLog;
import com.autopilot.repository.DeploymentLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Centralized logging service for the deployment pipeline.
 * <p>
 * Every log entry is:
 *   1. Persisted to the deployment_logs table (for history / initial SSE catch-up).
 *   2. Published to a Redis pub/sub channel so SSE subscribers get real-time updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentLogService {

    private final DeploymentLogRepository logRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Redis pub/sub channel prefix. Full channel = prefix + deploymentId.
     */
    private static final String CHANNEL_PREFIX = "autopilot:logs:";

    // ───────────────────────────── write ──────────────────────────────

    public void info(String deploymentId, String stage, String message) {
        writeLog(deploymentId, stage, "INFO", message);
    }

    public void warn(String deploymentId, String stage, String message) {
        writeLog(deploymentId, stage, "WARN", message);
    }

    public void error(String deploymentId, String stage, String message) {
        writeLog(deploymentId, stage, "ERROR", message);
    }

    public void debug(String deploymentId, String stage, String message) {
        writeLog(deploymentId, stage, "DEBUG", message);
    }

    /**
     * Publish a special sentinel event so SSE clients know the pipeline is done.
     */
    public void complete(String deploymentId) {
        publishToRedis(deploymentId, "__COMPLETE__");
    }

    // ───────────────────────────── read ───────────────────────────────

    /**
     * Fetch all historical log entries for a deployment (used for SSE catch-up).
     */
    public List<DeploymentLog> getLogsForDeployment(String deploymentId) {
        return logRepository.findByDeploymentIdOrderByTimestampAsc(deploymentId);
    }

    // ───────────────────────────── helpers ────────────────────────────

    public static String sanitizeMessage(String msg) {
        if (msg == null) return null;
        // 1. Mask RDS auto-generated master password patterns like: APxxxxxx!
        msg = msg.replaceAll("AP[a-zA-Z0-9]{16}!", "[REDACTED_PASSWORD]");
        // 2. Mask local fallback password patterns like: APxxxxxx
        msg = msg.replaceAll("AP[a-zA-Z0-9]{10}", "[REDACTED_PASSWORD]");
        // 3. Mask passwords in connection strings (e.g. mysql://user:pass@host:port/db)
        msg = msg.replaceAll("([a-zA-Z0-9]+://[^:]+:)([^@\\s]+)(@.+)", "$1[REDACTED_PASSWORD]$3");
        // 4. Mask AWS credentials and password variables (e.g. password=xyz)
        msg = msg.replaceAll("(?i)(aws_access_key_id|aws_secret_access_key|accesskey|secretkey|password|pass|pwd)\\s*[=:]\\s*['\"]?[a-zA-Z0-9/+=]{10,40}['\"]?", "$1=[REDACTED]");
        return msg;
    }

    private void writeLog(String deploymentId, String stage, String level, String message) {
        String sanitizedMessage = sanitizeMessage(message);
        
        // 1. Persist to DB
        DeploymentLog entry = new DeploymentLog(deploymentId, stage, level, sanitizedMessage);
        logRepository.save(entry);

        // 2. Publish JSON payload to Redis pub/sub
        try {
            String json = objectMapper.writeValueAsString(entry);
            publishToRedis(deploymentId, json);
        } catch (Exception e) {
            log.error("Failed to publish log to Redis for deployment {}: {}", deploymentId, e.getMessage());
        }

        // 3. Also log to server console for ops visibility
        log.info("[{}] [{}] [{}] {}", deploymentId, stage, level, sanitizedMessage);
    }

    private void publishToRedis(String deploymentId, String payload) {
        try {
            redisTemplate.convertAndSend(CHANNEL_PREFIX + deploymentId, payload);
        } catch (Exception e) {
            log.error("Redis publish failed for deployment {}: {}", deploymentId, e.getMessage());
        }
    }

    /**
     * Returns the Redis channel name for a given deployment.
     */
    public static String channelFor(String deploymentId) {
        return CHANNEL_PREFIX + deploymentId;
    }
}
