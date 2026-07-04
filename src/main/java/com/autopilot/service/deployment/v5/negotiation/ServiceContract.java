package com.autopilot.service.deployment.v5.negotiation;

import lombok.Builder;
import lombok.Value;
import java.util.Map;

/**
 * Immutable contract for a generic service dependency (database, cache, queue, search, storage, AI, vector DB).
 *
 * @since V5.2
 */
@Value
@Builder
public class ServiceContract {
    String serviceId;
    String serviceType;           // DATABASE, CACHE, QUEUE, SEARCH, STORAGE, AI_GATEWAY, VECTOR_DB
    DependencyContract dependency;
    ConfigurationContractV5 configuration;
    MigrationContract migration;
    CredentialContract credentials;
}
