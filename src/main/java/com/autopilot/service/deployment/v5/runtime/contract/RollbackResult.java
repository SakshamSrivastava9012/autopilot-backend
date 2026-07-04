package com.autopilot.service.deployment.v5.runtime.contract;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Result returned by an ExecutionNode after a rollback operation.
 *
 * @since V5.4
 */
@Value
@Builder
public class RollbackResult {
    boolean success;
    String nodeId;
    String message;
    List<String> logs;
    List<String> warnings;
    long durationMs;
}
