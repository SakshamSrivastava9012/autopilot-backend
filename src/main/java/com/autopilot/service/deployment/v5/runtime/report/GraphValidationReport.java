package com.autopilot.service.deployment.v5.runtime.report;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Report generated during execution graph validation (e.g. cycle detection, missing dependencies).
 *
 * @since V5.4
 */
@Value
@Builder
public class GraphValidationReport {
    boolean valid;
    int nodeCount;
    int edgeCount;
    List<String> topologicalOrder;
    List<String> parallelCandidates;
    List<String> errors;
    List<String> warnings;
}
