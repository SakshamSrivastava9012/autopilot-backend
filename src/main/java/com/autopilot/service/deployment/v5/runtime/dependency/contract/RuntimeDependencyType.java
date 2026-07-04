package com.autopilot.service.deployment.v5.runtime.dependency.contract;

/**
 * Generalized runtime dependency types.
 *
 * @since V5.4 — ADR-009
 */
public enum RuntimeDependencyType {
    SQL_DATABASE,
    NOSQL_DATABASE,
    CACHE,
    QUEUE,
    SEARCH,
    OBJECT_STORAGE,
    VECTOR_DATABASE,
    MESSAGE_BUS
}
