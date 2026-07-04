package com.autopilot.service.deployment.v5.runtime.environment.mapper;

import com.autopilot.service.deployment.v5.runtime.environment.resolver.RuntimeConnectionContract;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Universal Framework Configuration Mapper.
 *
 * Translates generic RuntimeConnectionContracts into framework-specific environment variables.
 * Enforces exactly ONE deterministic framework mapping per target application.
 *
 * Rules:
 *   Spring Boot SQL    → ONLY SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
 *   Spring Boot Mongo  → ONLY SPRING_DATA_MONGODB_URI (NEVER SPRING_DATASOURCE_URL)
 *   Node Mongo         → ONLY MONGODB_URI
 *   Node SQL           → ONLY DATABASE_URL
 *   Frontend (React/Angular/Vue) → ONLY frontend variables. NEVER inject SPRING_DATASOURCE, DB_USER, DB_PASSWORD, DATABASE_URL
 *
 * @since V5.3 — ADR-010 / Milestone 5.3
 */
@Service
public class FrameworkConfigurationMapper {

    private static final Set<String> FRONTEND_FRAMEWORKS = Set.of(
            "react", "react_vite", "angular", "vue", "svelte", "static", "html"
    );

    public Map<String, String> mapToFramework(List<RuntimeConnectionContract> connections, String framework) {
        String fw = framework != null ? framework.toLowerCase() : "generic";
        System.out.println("🗺️ Framework Configuration Mapper — Mapping " + connections.size()
                + " connections for framework: [" + fw + "]");

        // RULE: Frontend containers NEVER receive database/backend env vars
        if (isFrontendFramework(fw)) {
            System.out.println("   ℹ️ Frontend framework detected — Skipping all database/backend env injection.");
            return Collections.emptyMap();
        }

        Map<String, String> env = new LinkedHashMap<>();

        for (RuntimeConnectionContract conn : connections) {
            if (fw.contains("spring") || fw.contains("quarkus") || fw.contains("micronaut")) {
                mapSpringBoot(conn, env);
            } else if (fw.contains("laravel") || fw.contains("php")) {
                mapLaravel(conn, env);
            } else if (fw.contains("django") || fw.contains("flask") || fw.contains("fastapi") || fw.contains("python")) {
                mapPython(conn, env);
            } else if (fw.contains("rails") || fw.contains("ruby")) {
                mapRails(conn, env);
            } else if (fw.contains("node") || fw.contains("express") || fw.contains("nest") || fw.contains("next") || fw.contains("nuxt")) {
                mapNode(conn, env);
            } else {
                mapGeneric(conn, env);
            }
        }

        return Collections.unmodifiableMap(env);
    }

    private boolean isFrontendFramework(String fw) {
        for (String frontendFw : FRONTEND_FRAMEWORKS) {
            if (fw.contains(frontendFw)) return true;
        }
        return false;
    }

    private void mapSpringBoot(RuntimeConnectionContract conn, Map<String, String> env) {
        String depId = conn.getDependencyId().toLowerCase();
        String uri = conn.getUri() != null ? conn.getUri() : "";

        if (depId.contains("redis") || depId.contains("cache")) {
            env.put("SPRING_REDIS_HOST", conn.getHost());
            env.put("SPRING_REDIS_PORT", String.valueOf(conn.getPort()));
        } else if (depId.contains("mongo")) {
            // Spring Boot Mongo → ONLY SPRING_DATA_MONGODB_URI, NEVER SPRING_DATASOURCE_URL
            String mongoUri = uri.startsWith("mongodb") ? uri : "mongodb://" + conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabase();
            env.put("SPRING_DATA_MONGODB_URI", mongoUri);
        } else if (depId.contains("kafka")) {
            env.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", conn.getHost() + ":" + conn.getPort());
        } else if (depId.contains("rabbitmq") || depId.contains("amqp")) {
            env.put("SPRING_RABBITMQ_HOST", conn.getHost());
            env.put("SPRING_RABBITMQ_PORT", String.valueOf(conn.getPort()));
            env.put("SPRING_RABBITMQ_USERNAME", conn.getUsername());
            env.put("SPRING_RABBITMQ_PASSWORD", conn.getPassword());
        } else {
            // SQL databases: MySQL, Postgres, etc.
            String jdbcUrl = uri;
            if (!jdbcUrl.startsWith("jdbc:")) {
                // Build JDBC URI from connection details using correct protocol
                if (depId.contains("mysql") || depId.contains("mariadb")) {
                    jdbcUrl = "jdbc:mysql://" + conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabase();
                } else {
                    jdbcUrl = "jdbc:postgresql://" + conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabase();
                }
            }
            env.put("SPRING_DATASOURCE_URL", jdbcUrl);
            env.put("SPRING_DATASOURCE_USERNAME", conn.getUsername());
            env.put("SPRING_DATASOURCE_PASSWORD", conn.getPassword());
        }
    }

    private void mapNode(RuntimeConnectionContract conn, Map<String, String> env) {
        String depId = conn.getDependencyId().toLowerCase();
        String uri = conn.getUri() != null ? conn.getUri() : "";

        if (depId.contains("redis") || depId.contains("cache")) {
            env.put("REDIS_URL", uri.startsWith("redis://") ? uri : "redis://" + conn.getHost() + ":" + conn.getPort());
        } else if (depId.contains("mongo")) {
            // Node Mongo → ONLY MONGODB_URI, never DATABASE_URL
            String mongoUri = uri.startsWith("mongodb") ? uri : "mongodb://" + conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabase();
            env.put("MONGODB_URI", mongoUri);
        } else if (depId.contains("kafka")) {
            env.put("KAFKA_BROKERS", conn.getHost() + ":" + conn.getPort());
        } else {
            // SQL → DATABASE_URL
            env.put("DATABASE_URL", uri);
        }
    }

    private void mapLaravel(RuntimeConnectionContract conn, Map<String, String> env) {
        String depId = conn.getDependencyId().toLowerCase();
        if (depId.contains("redis") || depId.contains("cache")) {
            env.put("REDIS_HOST", conn.getHost());
            env.put("REDIS_PORT", String.valueOf(conn.getPort()));
        } else if (depId.contains("mongo")) {
            String uri = conn.getUri() != null ? conn.getUri() : "";
            env.put("MONGODB_URI", uri.startsWith("mongodb") ? uri : "mongodb://" + conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabase());
        } else {
            env.put("DB_HOST", conn.getHost());
            env.put("DB_PORT", String.valueOf(conn.getPort()));
            env.put("DB_DATABASE", conn.getDatabase());
            env.put("DB_USERNAME", conn.getUsername());
            env.put("DB_PASSWORD", conn.getPassword());
        }
    }

    private void mapPython(RuntimeConnectionContract conn, Map<String, String> env) {
        String depId = conn.getDependencyId().toLowerCase();
        if (depId.contains("redis") || depId.contains("cache")) {
            String uri = conn.getUri() != null ? conn.getUri() : "";
            env.put("REDIS_URL", uri.startsWith("redis://") ? uri : "redis://" + conn.getHost() + ":" + conn.getPort());
        } else if (depId.contains("mongo")) {
            String uri = conn.getUri() != null ? conn.getUri() : "";
            env.put("MONGODB_URI", uri.startsWith("mongodb") ? uri : "mongodb://" + conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabase());
        } else {
            env.put("DATABASE_URL", conn.getUri());
        }
    }

    private void mapRails(RuntimeConnectionContract conn, Map<String, String> env) {
        String depId = conn.getDependencyId().toLowerCase();
        if (depId.contains("redis") || depId.contains("cache")) {
            env.put("REDIS_URL", conn.getUri());
        } else if (depId.contains("mongo")) {
            env.put("MONGODB_URI", conn.getUri());
        } else {
            env.put("DATABASE_URL", conn.getUri());
        }
    }

    private void mapGeneric(RuntimeConnectionContract conn, Map<String, String> env) {
        String depId = conn.getDependencyId().toLowerCase();
        if (depId.contains("redis") || depId.contains("cache")) {
            env.put("REDIS_URL", conn.getUri());
        } else if (depId.contains("mongo")) {
            env.put("MONGODB_URI", conn.getUri());
        } else {
            env.put("DATABASE_URL", conn.getUri());
            env.put("DB_HOST", conn.getHost());
            env.put("DB_PORT", String.valueOf(conn.getPort()));
            env.put("DB_USER", conn.getUsername());
            env.put("DB_PASSWORD", conn.getPassword());
            env.put("DB_NAME", conn.getDatabase());
        }
    }
}
