package com.autopilot.service.deployment.v5.runtime.environment.report;

import com.autopilot.service.deployment.v5.runtime.environment.resolver.RuntimeConnectionContract;
import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Immutable audit report for negotiated runtime connection contracts.
 *
 * @since V5.6
 */
@Value
@Builder
public class RuntimeConnectionReport {
    String environmentId;
    int connectionContractsCount;
    List<RuntimeConnectionContract> connectionContracts;
    long generationTimeMs;
}
