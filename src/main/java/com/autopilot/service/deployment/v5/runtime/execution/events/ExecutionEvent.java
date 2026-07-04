package com.autopilot.service.deployment.v5.runtime.execution.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public abstract class ExecutionEvent {
    private String eventId;
    private String sessionId;
    private long timestamp;
    private String eventType;
    private String stage;
    private String message;
}
