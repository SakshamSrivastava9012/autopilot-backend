package com.autopilot.service.deployment.v5.runtime.contract;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;

/**
 * Result returned by an ExecutionNode after execution.
 *
 * @since V5.4
 */
@Value
@Builder
public class ExecutionResult {
    boolean success;
    String nodeId;
    String message;
    Map<String, Object> outputs;
    List<String> logs;
    List<String> warnings;
    long executionDurationMs;
}
