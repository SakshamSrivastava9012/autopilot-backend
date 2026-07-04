package com.autopilot.service.deployment.v5.runtime.timeline;

import com.autopilot.service.deployment.v5.runtime.graph.NodeState;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks node-level execution events, status, durations, threads, and warnings.
 * Thread-safe for parallel node execution.
 *
 * @since V5.4 — ADR-007
 */
public class ExecutionTimeline {

    private final List<TimelineEntry> entries = new CopyOnWriteArrayList<>();

    public void record(String nodeId, String nodeName, NodeState status, long startTime, long finishTime,
                       int retries, String threadName, List<String> warnings) {
        entries.add(TimelineEntry.builder()
                .nodeId(nodeId)
                .nodeName(nodeName)
                .status(status)
                .startTimeEpoch(startTime)
                .finishTimeEpoch(finishTime)
                .durationMs(Math.max(0, finishTime - startTime))
                .retries(retries)
                .threadName(threadName != null ? threadName : Thread.currentThread().getName())
                .warnings(warnings != null ? warnings : Collections.emptyList())
                .build());
    }

    public List<TimelineEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @Value
    @Builder
    public static class TimelineEntry {
        String nodeId;
        String nodeName;
        NodeState status;
        long startTimeEpoch;
        long finishTimeEpoch;
        long durationMs;
        int retries;
        String threadName;
        List<String> warnings;
    }
}
