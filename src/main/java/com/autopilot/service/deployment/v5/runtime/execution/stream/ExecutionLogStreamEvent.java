package com.autopilot.service.deployment.v5.runtime.execution.stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionLogStreamEvent {
    public enum LogSource {
        SSM_STDOUT,
        SSM_STDERR,
        DOCKER,
        CLOUD_INIT,
        SPRING_BOOT,
        FRONTEND,
        SYSTEM
    }

    private String logId;
    private String sessionId;
    private LogSource source;
    private String message;
    private long timestamp;
    private String streamLevel; // INFO, WARN, ERROR
}
