package com.autopilot.service.deployment.v5.negotiation;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Classifies detected endpoints as development or production.
 * Pure string analysis — no DNS resolution, no TCP connections, no network I/O.
 *
 * @since V5.2
 */
@Service
public class ConfigurationClassifier {

    private static final List<String> LOCALHOST_PATTERNS = Arrays.asList(
            "localhost", "127.0.0.1", "0.0.0.0", "host.docker.internal", "::1"
    );

    private static final List<String> CLOUD_DB_PATTERNS = Arrays.asList(
            "amazonaws.com", "rds.amazonaws.com", "elasticache.amazonaws.com",
            "neon.tech", "supabase.com", "supabase.co", "planetscale.com",
            "railway.internal", "render.com", "fly.dev",
            "mongodb.net", "redis-cloud", "upstash.io",
            "azure.com", "database.windows.net",
            "cloudsql", "sql.goog",
            "elephantsql.com", "cockroachlabs.cloud",
            "digitaloceanspaces.com", "aiven.io"
    );

    private static final List<String> PRIVATE_PATTERNS = Arrays.asList(
            "10.", "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.", "172.24.",
            "172.25.", "172.26.", "172.27.", "172.28.", "172.29.",
            "172.30.", "172.31.", "192.168."
    );

    /**
     * Classify a connection hint/URI as dev or production context.
     * No network I/O — pure string pattern matching.
     */
    public EndpointClassification classify(String connectionHint) {
        if (connectionHint == null || connectionHint.isBlank()) {
            return EndpointClassification.UNKNOWN;
        }

        String lower = connectionHint.toLowerCase();

        for (String pattern : LOCALHOST_PATTERNS) {
            if (lower.contains(pattern)) {
                return EndpointClassification.LOCALHOST;
            }
        }

        for (String pattern : CLOUD_DB_PATTERNS) {
            if (lower.contains(pattern)) {
                return EndpointClassification.CLOUD_DATABASE;
            }
        }

        for (String pattern : PRIVATE_PATTERNS) {
            if (lower.contains(pattern)) {
                return EndpointClassification.PRIVATE_NETWORK;
            }
        }

        // If it looks like a docker-compose service name (no dots, no protocol)
        if (!lower.contains(".") && !lower.contains("://")) {
            return EndpointClassification.DOCKER_SERVICE;
        }

        // If it has a real domain
        if (lower.contains(".") && !lower.startsWith("$")) {
            return EndpointClassification.PUBLIC_IP;
        }

        return EndpointClassification.UNKNOWN;
    }

    /**
     * Is this a development-only endpoint that should not be used in production?
     */
    public boolean isDevelopmentEndpoint(EndpointClassification classification) {
        return classification == EndpointClassification.LOCALHOST
                || classification == EndpointClassification.DOCKER_SERVICE;
    }

    /**
     * Is this a production-grade endpoint that can be reused directly?
     */
    public boolean isProductionEndpoint(EndpointClassification classification) {
        return classification == EndpointClassification.CLOUD_DATABASE
                || classification == EndpointClassification.PUBLIC_IP;
    }
}
