package com.autopilot.service.deployment.v5.runtime.environment.sanitizer;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Universal Configuration Sanitizer.
 * Removes conflicting, duplicate, obsolete, or repository-embedded development values.
 * Enforces framework single-model rules: no mixing SPRING_DATASOURCE + DATABASE_URL,
 * no SPRING_DATASOURCE_URL inside a Mongo-only application, etc.
 *
 * @since V5.3 — ADR-010 / Milestone 5.3
 */
@Service
public class ConfigurationSanitizer {

    private static final Set<String> BACKEND_ONLY_KEYS = Set.of(
            "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
            "SPRING_DATA_MONGODB_URI", "SPRING_REDIS_HOST", "SPRING_REDIS_PORT",
            "SPRING_KAFKA_BOOTSTRAP_SERVERS", "SPRING_RABBITMQ_HOST",
            "DATABASE_URL", "DB_HOST", "DB_PORT", "DB_USER", "DB_PASSWORD", "DB_NAME",
            "DB_DATABASE", "DB_USERNAME",
            "MONGODB_URI", "REDIS_URL", "KAFKA_BROKERS"
    );

    private static final Set<String> FRONTEND_FRAMEWORKS = Set.of(
            "react", "react_vite", "angular", "vue", "svelte", "static", "html"
    );

    public SanitizationResult sanitize(Map<String, String> rawEnv, String framework) {
        System.out.println("🧹 Configuration Sanitizer — Sanitizing environment variables for framework: [" + framework + "]");

        Map<String, String> sanitized = new LinkedHashMap<>();
        List<String> removedVars = new ArrayList<>();
        String fw = framework != null ? framework.toLowerCase() : "";

        boolean isFrontend = FRONTEND_FRAMEWORKS.stream().anyMatch(fw::contains);

        for (Map.Entry<String, String> entry : rawEnv.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() != null ? entry.getValue() : "";

            // 1. Filter out repo-level dev values
            if (isDevelopmentValue(val)) {
                removedVars.add(key + " (Reason: Hardcoded development value)");
                continue;
            }

            // 2. Frontend frameworks must NEVER receive backend database vars
            if (isFrontend && BACKEND_ONLY_KEYS.contains(key.toUpperCase())) {
                removedVars.add(key + " (Reason: Backend-only variable injected into frontend container)");
                continue;
            }

            // 3. Enforce framework single-model rules
            if (fw.contains("spring")) {
                // If SPRING_DATASOURCE_URL exists, remove generic DATABASE_URL
                if ("DATABASE_URL".equalsIgnoreCase(key) && rawEnv.containsKey("SPRING_DATASOURCE_URL")) {
                    removedVars.add(key + " (Reason: Conflict with SPRING_DATASOURCE_URL)");
                    continue;
                }
                // If SPRING_DATA_MONGODB_URI exists, remove SPRING_DATASOURCE vars (Mongo app, not SQL)
                if (key.toUpperCase().startsWith("SPRING_DATASOURCE") && rawEnv.containsKey("SPRING_DATA_MONGODB_URI")) {
                    removedVars.add(key + " (Reason: Mongo application — SPRING_DATASOURCE not applicable)");
                    continue;
                }
            }

            sanitized.put(key, val);
        }

        return new SanitizationResult(Collections.unmodifiableMap(sanitized), Collections.unmodifiableList(removedVars));
    }

    private boolean isDevelopmentValue(String val) {
        String lower = val.toLowerCase().trim();
        return lower.contains("localhost:5432/dev")
                || lower.contains("localhost:3306/dev")
                || lower.contains("localhost:27017/dev")
                || lower.equals("root")
                || lower.equals("admin")
                || lower.equals("password")
                || lower.equals("127.0.0.1");
    }

    @lombok.Value
    public static class SanitizationResult {
        Map<String, String> sanitizedEnvironment;
        List<String> removedVariables;
    }
}
