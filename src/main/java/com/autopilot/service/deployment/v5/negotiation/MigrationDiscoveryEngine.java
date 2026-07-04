package com.autopilot.service.deployment.v5.negotiation;

import com.autopilot.service.deployment.intelligence.v5.model.RepositoryModelV5;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * Detects migration tooling from the RepositoryModelV5.
 * Detection only — never executes migrations.
 *
 * @since V5.2
 */
@Service
public class MigrationDiscoveryEngine {

    /**
     * Discovers migration strategy from repository metadata.
     * Pure observation — no execution.
     */
    public MigrationContract discover(RepositoryModelV5 model) {
        System.out.println("🔄 Migration Discovery Engine — Analyzing migration tooling...");

        String workspace = model.getWorkspace();
        Set<String> languages = model.getLanguages();
        Set<String> frameworks = model.getFrameworks();

        // Flyway (Spring Boot / JVM)
        if (new File(workspace, "src/main/resources/db/migration").isDirectory()) {
            return MigrationContract.builder()
                    .tool("FLYWAY").migrationsPath("src/main/resources/db/migration")
                    .source("src/main/resources/db/migration/").autoMigrate(true).build();
        }

        // Liquibase
        if (new File(workspace, "src/main/resources/db/changelog").isDirectory()) {
            return MigrationContract.builder()
                    .tool("LIQUIBASE").migrationsPath("src/main/resources/db/changelog")
                    .source("src/main/resources/db/changelog/").autoMigrate(true).build();
        }

        // Prisma
        if (new File(workspace, "prisma/schema.prisma").exists()) {
            return MigrationContract.builder()
                    .tool("PRISMA").migrationsPath("prisma/migrations")
                    .source("prisma/schema.prisma").autoMigrate(false).build();
        }

        // Django
        if (frameworks.contains("Django")) {
            return MigrationContract.builder()
                    .tool("DJANGO").migrationsPath("migrations/")
                    .source("manage.py").autoMigrate(false).build();
        }

        // Laravel
        if (frameworks.contains("Laravel")) {
            return MigrationContract.builder()
                    .tool("LARAVEL").migrationsPath("database/migrations")
                    .source("artisan").autoMigrate(false).build();
        }

        // Rails
        if (frameworks.contains("Rails")) {
            return MigrationContract.builder()
                    .tool("RAILS").migrationsPath("db/migrate")
                    .source("Rakefile").autoMigrate(false).build();
        }

        // Alembic (FastAPI / Flask)
        if (new File(workspace, "alembic.ini").exists()) {
            return MigrationContract.builder()
                    .tool("ALEMBIC").migrationsPath("alembic/versions")
                    .source("alembic.ini").autoMigrate(false).build();
        }

        // Hibernate auto-update (JVM fallback)
        if (languages.contains("Java")) {
            return MigrationContract.builder()
                    .tool("HIBERNATE_AUTO").source("JVM detected — may use hibernate.ddl-auto")
                    .autoMigrate(true).build();
        }

        return MigrationContract.builder().tool("NONE").autoMigrate(false).build();
    }
}
