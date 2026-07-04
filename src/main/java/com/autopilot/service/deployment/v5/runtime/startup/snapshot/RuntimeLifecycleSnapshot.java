package com.autopilot.service.deployment.v5.runtime.startup.snapshot;

import com.autopilot.service.deployment.v5.runtime.startup.lifecycle.RuntimeLifecycleState;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of runtime lifecycle state, ports, probes, and timeline.
 *
 * @since V5.4 — ADR-011
 */
@Value
@Builder
public class RuntimeLifecycleSnapshot {
    String deploymentId;
    String containerId;
    long processPid;
    List<Integer> boundPorts;
    RuntimeLifecycleState lifecycleState;
    boolean readinessStatus;
    boolean healthStatus;
    List<String> timelineEntries;
    List<String> events;
    List<String> warnings;
    Map<String, String> metadata;
}
