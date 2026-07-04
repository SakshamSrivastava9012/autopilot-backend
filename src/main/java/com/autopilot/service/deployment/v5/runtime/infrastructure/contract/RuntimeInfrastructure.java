package com.autopilot.service.deployment.v5.runtime.infrastructure.contract;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Provisioned runtime infrastructure reference.
 *
 * @since V5.4 — ADR-008
 */
@Value
@Builder
public class RuntimeInfrastructure {
    String provider;
    String identifier;               // e.g. AWS ARN, Docker Network ID, RDS Endpoint
    String endpoint;                 // Host or IP
    InfrastructureResourceLifecycle status;
    Map<String, String> metadata;
    long creationTimeEpoch;
    Map<String, Object> runtimeProperties;
}
