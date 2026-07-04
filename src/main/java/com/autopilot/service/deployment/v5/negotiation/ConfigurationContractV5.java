package com.autopilot.service.deployment.v5.negotiation;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable contract describing exactly which configuration variables an application expects
 * and in which style they should be injected.
 *
 * Produced by ConfigurationNegotiationEngineV5. Consumed by the environment injector (future).
 *
 * @since V5.2 — ADR-005
 */
@Value
@Builder
public class ConfigurationContractV5 {
    String frameworkStyle;              // SPRING_DATASOURCE, DATABASE_URL, DB_HOST, MONGO_URI, etc.
    String environmentModel;            // e.g. "Spring Boot", "Node.js .env", "Django settings"

    Map<String, String> variables;      // The exact key-value pairs to inject
    Set<String> removedVariables;       // Variables explicitly excluded (conflict resolution)
    Set<String> generatedVariables;     // Variables Deployrix will generate (e.g. DB_NAME for new DBs)

    List<String> warnings;
    int confidence;                     // 0–100
}
