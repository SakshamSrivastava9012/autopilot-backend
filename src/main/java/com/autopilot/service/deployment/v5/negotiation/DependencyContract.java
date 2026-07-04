package com.autopilot.service.deployment.v5.negotiation;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;

/**
 * Immutable contract describing a single runtime dependency and how it should be provisioned.
 * Produced by the DependencyNegotiationEngine. Consumed by provisioning (future milestone).
 *
 * This contract describes INTENT — it never provisions, connects, or authenticates.
 *
 * @since V5.2 — ADR-005
 */
@Value
@Builder
public class DependencyContract {
    String dependencyId;
    String type;                      // MySQL, PostgreSQL, MongoDB, Redis, Kafka, etc.
    String provider;                  // e.g. "aws_rds", "mongo_atlas", "docker", "neon"
    String version;                   // e.g. "8.0", "16", "7.2" — or "unknown"

    String databaseName;
    String host;
    int port;
    String username;
    String password;                  // Only set if sourced from repository config — never generated
    String uri;                       // Full connection URI if detected

    OwnershipType ownership;
    ProviderPreference provisioningMode;
    EndpointClassification endpointClassification;

    boolean tls;
    String healthStrategy;            // TCP, HTTP, REDIS_PING, MONGO_PING, SQL_QUERY, etc.
    String migrationStrategy;         // FLYWAY, LIQUIBASE, PRISMA, ALEMBIC, NONE, etc.

    List<String> runtimeHints;
    Map<String, String> metadata;
}
