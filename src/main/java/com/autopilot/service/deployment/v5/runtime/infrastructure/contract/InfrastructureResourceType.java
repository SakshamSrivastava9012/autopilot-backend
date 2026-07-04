package com.autopilot.service.deployment.v5.runtime.infrastructure.contract;

/**
 * Generalized infrastructure resource types.
 * Infrastructure engine does not care about specific engines like MySQL or Postgres.
 *
 * @since V5.4 — ADR-008
 */
public enum InfrastructureResourceType {
    DATABASE,
    CACHE,
    QUEUE,
    SEARCH,
    OBJECT_STORAGE,
    FILE_STORAGE,
    LOAD_BALANCER,
    NETWORK,
    DNS,
    CERTIFICATE,
    COMPUTE,
    CONTAINER_RUNTIME
}
