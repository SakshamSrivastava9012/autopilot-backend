package com.autopilot.service.deployment.v5.runtime.contract;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Health verification returned by an ExecutionNode.
 *
 * @since V5.4
 */
@Value
@Builder
public class ExecutionHealth {
    boolean healthy;
    String nodeId;
    String statusMessage;
    List<String> diagnostics;
}
