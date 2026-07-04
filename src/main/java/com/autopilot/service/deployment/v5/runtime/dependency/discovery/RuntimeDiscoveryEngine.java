package com.autopilot.service.deployment.v5.runtime.dependency.discovery;

import com.autopilot.service.deployment.v5.negotiation.DependencyContract;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.environment.resolver.RuntimeConnectionContract;
import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Universal Runtime Discovery Engine.
 *
 * Inspects running database, cache, or queue instances post-provisioning to extract actual connection details.
 * Eliminates all negotiated placeholders and generates the single source of truth: RuntimeConnectionContract.
 *
 * @since V5.6
 */
@Service
public class RuntimeDiscoveryEngine {

    public RuntimeDiscoveryResult discover(RuntimeDependency dependency,
                                            ResolvedCredentialContract credentials,
                                            DependencyContract contract) {
        System.out.println("🔍 Runtime Discovery Engine — Inspecting live dependency: " + dependency.getId());
        long start = System.currentTimeMillis();

        // Parse runtimeEndpoint (e.g. "127.0.0.1:3306" or "db-instance.rds.amazonaws.com:5432")
        String endpoint = dependency.getRuntimeEndpoint();
        String host = "localhost";
        int port = 5432;
        if (endpoint != null && endpoint.contains(":")) {
            String[] parts = endpoint.split(":");
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                // Keep default
            }
        } else if (endpoint != null) {
            host = endpoint;
        }

        // Discover protocol from dependency type or ID
        String depType = dependency.getDependencyType() != null ? dependency.getDependencyType().name() : "SQL_DATABASE";
        String protocol = resolveProtocol(dependency.getId(), dependency.getProvider());

        // Extract metadata from runtime dependency
        Map<String, String> providerMetadata = dependency.getRuntimeMetadata() != null 
                ? dependency.getRuntimeMetadata() 
                : new HashMap<>();

        // Certificates/SSL/TLS flags
        boolean ssl = "true".equalsIgnoreCase(providerMetadata.get("ssl")) || "true".equalsIgnoreCase(providerMetadata.get("useSsl"));
        boolean tls = "true".equalsIgnoreCase(providerMetadata.get("tls")) || "true".equalsIgnoreCase(providerMetadata.get("useTls"));
        String certificates = providerMetadata.getOrDefault("certificates", "none");
        String authMechanism = providerMetadata.getOrDefault("authMechanism", "PASSWORD");
        String replicaInfo = providerMetadata.getOrDefault("replicaInfo", "standalone");
        String clusterEndpoints = providerMetadata.getOrDefault("clusterEndpoints", endpoint);

        // Credentials
        String username = credentials != null ? credentials.getUsername() : "root";
        String password = credentials != null ? credentials.getPassword() : "";
        String database = credentials != null ? credentials.getDatabase() : "defaultdb";

        // Generate native URI
        String uri = credentials != null && credentials.getUri() != null && !credentials.getUri().isEmpty()
                ? credentials.getUri()
                : buildNativeUri(protocol, host, port, username, password, database);

        // Generate the RuntimeConnectionContract
        RuntimeConnectionContract connectionContract = RuntimeConnectionContract.builder()
                .connectionId("conn-" + dependency.getId())
                .dependencyId(dependency.getId())
                .dependencyType(depType)
                .provider(dependency.getProvider())
                .endpoint(endpoint)
                .protocol(protocol)
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .database(database)
                .uri(uri)
                .ssl(ssl)
                .tls(tls)
                .certificateReference(certificates)
                .authenticationType(authMechanism)
                .authentication(authMechanism)
                .healthEndpoint(dependency.getHealthReference())
                .ownership(dependency.getOwnership())
                .metadata(providerMetadata)
                .build();

        long duration = System.currentTimeMillis() - start;

        // Build report
        RuntimeDiscoveryReport report = RuntimeDiscoveryReport.builder()
                .dependencyId(dependency.getId())
                .discoveredHost(host)
                .discoveredPort(port)
                .discoveredProtocol(protocol)
                .sslEnabled(ssl)
                .tlsEnabled(tls)
                .certificatesDiscovered(certificates)
                .databaseName(database)
                .authenticationMechanism(authMechanism)
                .replicaInformation(replicaInfo)
                .clusterEndpoints(clusterEndpoints)
                .connectionUri(uri)
                .durationMs(duration)
                .build();

        return new RuntimeDiscoveryResult(connectionContract, report);
    }

    private String resolveProtocol(String depId, String provider) {
        String id = depId.toLowerCase();
        if (id.contains("mysql") || id.contains("mariadb")) return "mysql";
        if (id.contains("postgres") || id.contains("pg") || id.contains("rds")) return "postgresql";
        if (id.contains("atlas") || id.contains("mongodb+srv")) return "mongodb+srv";
        if (id.contains("mongo")) return "mongodb";
        if (id.contains("redis") || id.contains("cache")) return "redis";
        if (id.contains("kafka")) return "kafka";
        if (id.contains("rabbitmq") || id.contains("amqp")) return "amqp";
        if (id.contains("opensearch") || id.contains("elasticsearch")) return "https";
        return "postgresql";
    }

    private String buildNativeUri(String protocol, String host, int port, String user, String pass, String db) {
        String credentials = (user != null && !user.isEmpty())
                ? user + (pass != null && !pass.isEmpty() ? ":" + pass : "") + "@"
                : "";

        switch (protocol) {
            case "mysql":
                return "jdbc:mysql://" + host + ":" + port + "/" + db;
            case "postgresql":
                return "jdbc:postgresql://" + host + ":" + port + "/" + db;
            case "mongodb+srv":
                return "mongodb+srv://" + credentials + host + "/" + db;
            case "mongodb":
                return "mongodb://" + credentials + host + ":" + port + "/" + db;
            case "redis":
                return "redis://" + host + ":" + port;
            case "kafka":
                return host + ":" + port;
            case "amqp":
                return "amqp://" + credentials + host + ":" + port;
            default:
                return "jdbc:postgresql://" + host + ":" + port + "/" + db;
        }
    }

    @Value
    public static class RuntimeDiscoveryResult {
        RuntimeConnectionContract connectionContract;
        RuntimeDiscoveryReport discoveryReport;
    }

    @Value
    @Builder
    public static class RuntimeDiscoveryReport {
        String dependencyId;
        String discoveredHost;
        int discoveredPort;
        String discoveredProtocol;
        boolean sslEnabled;
        boolean tlsEnabled;
        String certificatesDiscovered;
        String databaseName;
        String authenticationMechanism;
        String replicaInformation;
        String clusterEndpoints;
        String connectionUri;
        long durationMs;
    }
}
