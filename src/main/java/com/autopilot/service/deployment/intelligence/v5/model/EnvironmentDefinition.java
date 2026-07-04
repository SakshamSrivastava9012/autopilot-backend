package com.autopilot.service.deployment.intelligence.v5.model;

import lombok.Builder;
import lombok.Value;

/**
 * Records a configuration variable observed in the repository.
 * This is observation only — the engine never injects or mutates values.
 *
 * @since V5
 */
@Value
@Builder
public class EnvironmentDefinition {
    String key;              // e.g. "SPRING_DATASOURCE_URL"
    String inferredType;     // e.g. "JDBC_URL", "MONGO_URI", "SECRET", "PORT"
    String defaultValue;     // e.g. "jdbc:mysql://localhost:3306/db" — masked if secret
    String source;           // e.g. ".env.production:4", "application.yml:spring.datasource.url"
    boolean isSecret;
    boolean isPlaceholder;   // e.g. ${DATABASE_URL} or <YOUR_KEY>
    double confidence;
}
