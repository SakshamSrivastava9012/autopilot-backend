package com.autopilot.service.deployment.intelligence.v5.model;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Describes a detected runtime dependency (database, cache, queue, search, storage).
 * Detection only — never provisions or connects.
 *
 * @since V5
 */
@Value
@Builder
public class DependencyDefinition {
    String type;             // PostgreSQL, MongoDB, Redis, Kafka, etc.
    String name;             // Identifier (e.g. "primary-db", "session-cache")
    boolean required;
    String detectedVersion;
    String detectedProvider; // e.g. "mongo_atlas", "aws_rds", "docker", "local"
    String connectionHint;   // e.g. "${MONGO_URI}" — a reference, never a real credential
    String source;           // Where this was detected (e.g. "application.yml:14")
    List<String> evidence;
    double confidence;
}
