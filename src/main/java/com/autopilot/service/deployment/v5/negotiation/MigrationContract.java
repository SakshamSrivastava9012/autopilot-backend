package com.autopilot.service.deployment.v5.negotiation;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable contract describing detected migration tooling.
 * Detection only — never executes migrations.
 *
 * @since V5.2
 */
@Value
@Builder
public class MigrationContract {
    String tool;            // FLYWAY, LIQUIBASE, PRISMA, ALEMBIC, RAILS, LARAVEL, DJANGO, HIBERNATE_AUTO, NONE
    String detectedVersion;
    String migrationsPath;  // e.g. "db/migration", "prisma/migrations"
    String source;          // Where this was detected
    boolean autoMigrate;    // Does the app auto-migrate on startup?
}
