package com.autopilot.service.deployment.v5.runtime.environment.resolver;

import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.infrastructure.snapshot.InfrastructureSnapshot;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Universal Runtime Connection Resolver.
 *
 * Resolves RuntimeDependency and ResolvedCredentialContract into immutable RuntimeConnectionContracts.
 * Enforces strict protocol-to-dependency mapping:
 *   MySQL      → jdbc:mysql://
 *   Postgres   → jdbc:postgresql://
 *   MongoDB    → mongodb://
 *   Mongo Atlas → mongodb+srv://
 *   Redis      → redis://
 *   Kafka      → bootstrap.servers
 *   RabbitMQ   → amqp://
 *   OpenSearch  → https://
 *
 * NEVER cross-maps protocols (e.g. MongoDB must NEVER produce jdbc:mysql://).
 *
 * @since V5.3 — ADR-010 / Milestone 5.3
 */
@Service
public class RuntimeConnectionResolver {

    public RuntimeConnectionContract resolveConnection(RuntimeDependency dependency,
                                                         ResolvedCredentialContract credentials,
                                                         InfrastructureSnapshot infraSnapshot) {
        String depId = dependency.getId();
        String id = depId.toLowerCase();
        System.out.println("🔗 Runtime Connection Resolver — Resolving connection contract for [" + depId + "]");

        String host = credentials != null && credentials.getHost() != null ? credentials.getHost() : "localhost";
        int port = credentials != null && credentials.getPort() > 0 ? credentials.getPort() : resolveDefaultPort(depId);
        String user = credentials != null ? credentials.getUsername() : "";
        String pass = credentials != null ? credentials.getPassword() : "";
        String db = credentials != null ? credentials.getDatabase() : "";

        // Build protocol-correct URI based on dependency type
        String uri = credentials != null && credentials.getUri() != null && !credentials.getUri().isEmpty()
                ? credentials.getUri()
                : buildNativeProtocolUri(depId, host, port, user, pass, db);

        // Enforce BUG 2 rules: Mongo must ONLY produce mongodb:// or mongodb+srv://. Never JDBC.
        if (depId.toLowerCase().contains("mongo") || 
            (dependency.getDependencyType() == com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependencyType.NOSQL_DATABASE 
             && dependency.getProvider() != null && dependency.getProvider().toLowerCase().contains("mongo"))) {
            if (uri == null || uri.startsWith("jdbc:") || !uri.startsWith("mongodb")) {
                uri = buildNativeProtocolUri(depId, host, port, user, pass, db);
            }
        }

        // Discover protocol from dependency ID
        String protocol = id.contains("mysql") ? "mysql" : (id.contains("mongo") ? "mongodb" : (id.contains("redis") ? "redis" : "postgresql"));
        String depType = dependency.getDependencyType() != null ? dependency.getDependencyType().name() : "SQL_DATABASE";

        return RuntimeConnectionContract.builder()
                .connectionId("conn-" + depId)
                .dependencyId(depId)
                .dependencyType(depType)
                .provider(dependency.getProvider())
                .protocol(protocol)
                .host(host)
                .port(port)
                .database(db)
                .username(user)
                .password(pass)
                .uri(uri)
                .ssl(false)
                .tls(false)
                .certificateReference("none")
                .authenticationType("PASSWORD")
                .authentication("PASSWORD")
                .healthEndpoint(dependency.getHealthReference())
                .ownership(dependency.getOwnership() != null ? dependency.getOwnership() : OwnershipType.PLATFORM)
                .metadata(dependency.getRuntimeMetadata() != null ? dependency.getRuntimeMetadata() : Collections.emptyMap())
                .build();
    }

    public List<RuntimeConnectionContract> resolveAllConnections(Map<String, RuntimeDependency> dependencies,
                                                                   Map<String, ResolvedCredentialContract> credentials,
                                                                   InfrastructureSnapshot infraSnapshot) {
        List<RuntimeConnectionContract> contracts = new ArrayList<>();
        if (dependencies != null) {
            for (Map.Entry<String, RuntimeDependency> entry : dependencies.entrySet()) {
                String depId = entry.getKey();
                RuntimeDependency dep = entry.getValue();
                ResolvedCredentialContract cred = credentials != null ? credentials.get(depId) : null;
                contracts.add(resolveConnection(dep, cred, infraSnapshot));
            }
        }
        return Collections.unmodifiableList(contracts);
    }

    /**
     * Builds a protocol-correct URI based on the dependency type.
     * Each dependency maps ONLY to its native protocol. Never cross-maps.
     */
    private String buildNativeProtocolUri(String depId, String host, int port,
                                          String user, String pass, String db) {
        String id = depId.toLowerCase();
        String credentials = (user != null && !user.isEmpty())
                ? user + (pass != null && !pass.isEmpty() ? ":" + pass : "") + "@"
                : "";

        if (id.contains("mysql") || id.contains("mariadb")) {
            return "jdbc:mysql://" + host + ":" + port + "/" + db;
        }
        if (id.contains("postgres") || id.contains("pg") || id.contains("rds")) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + db;
        }
        if (id.contains("atlas") || id.contains("mongodb+srv")) {
            return "mongodb+srv://" + credentials + host + "/" + db;
        }
        if (id.contains("mongo")) {
            return "mongodb://" + credentials + host + ":" + port + "/" + db;
        }
        if (id.contains("redis") || id.contains("cache")) {
            return "redis://" + host + ":" + port;
        }
        if (id.contains("kafka")) {
            return host + ":" + port; // Kafka uses bootstrap.servers format
        }
        if (id.contains("rabbitmq") || id.contains("amqp")) {
            return "amqp://" + credentials + host + ":" + port;
        }
        if (id.contains("opensearch") || id.contains("elasticsearch")) {
            return "https://" + host + ":" + port;
        }

        // Fallback: generic JDBC (most SQL databases)
        return "jdbc:postgresql://" + host + ":" + port + "/" + db;
    }

    /**
     * Resolves the default port for a dependency type when none is provided.
     */
    private int resolveDefaultPort(String depId) {
        if (depId == null) return 5432;
        String id = depId.toLowerCase();

        if (id.contains("mysql") || id.contains("mariadb")) return 3306;
        if (id.contains("postgres") || id.contains("pg") || id.contains("rds")) return 5432;
        if (id.contains("mongo")) return 27017;
        if (id.contains("redis") || id.contains("cache")) return 6379;
        if (id.contains("kafka")) return 9092;
        if (id.contains("rabbitmq") || id.contains("amqp")) return 5672;
        if (id.contains("opensearch") || id.contains("elasticsearch")) return 9200;

        return 5432;
    }
}
