package com.autopilot.intelligence;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Component 7: Environment Injection Engine
 *
 * Generates the `docker run -e` flags from the normalized environment map.
 * Supports multi-injection strategy — when a variable name is uncertain,
 * injects ALL possible variants to maximize compatibility.
 */
@Component
public class EnvInjectionEngine {

    /**
     * Multi-injection variant map.
     * When we detect a certain dependency, inject all known variable names
     * that ANY framework might expect.
     */
    private static final Map<String, List<String>> MULTI_INJECT_VARIANTS = Map.of(
            "mysql", List.of(
                    "DATABASE_URL", "DB_URL", "MYSQL_URL",
                    "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
                    "DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD",
                    "MYSQL_HOST", "MYSQL_PORT", "MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD"
            ),
            "postgres", List.of(
                    "DATABASE_URL", "DB_URL", "POSTGRES_URL", "PG_URL",
                    "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
                    "PGHOST", "PGPORT", "PGDATABASE", "PGUSER", "PGPASSWORD",
                    "DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD"
            ),
            "mongodb", List.of(
                    "DATABASE_URL", "MONGO_URL", "MONGO_URI", "MONGODB_URI",
                    "MONGO_HOST", "MONGO_PORT", "MONGO_DATABASE",
                    "SPRING_DATA_MONGODB_URI", "SPRING_DATA_MONGODB_DATABASE"
            ),
            "redis", List.of(
                    "REDIS_URL", "REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD",
                    "SPRING_DATA_REDIS_HOST", "SPRING_DATA_REDIS_PORT", "SPRING_DATA_REDIS_PASSWORD",
                    "CACHE_URL"
            )
    );

    /**
     * Build docker -e flags from the environment map.
     */
    public List<String> buildDockerEnvFlags(Map<String, String> envMap) {
        List<String> flags = new ArrayList<>();
        for (var entry : envMap.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                String key = entry.getKey().toUpperCase();
                
                // 🛑 Force AWS SDK to use EC2 Instance Profile IMDS credentials
                // By stripping hardcoded AWS keys from the container environment,
                // we guarantee the secure IAM Role is used automatically.
                if (key.contains("AWS") && (key.contains("ACCESS") || key.contains("SECRET") || key.contains("KEY"))) {
                    continue; // Skip AWS credentials
                }
                
                flags.add("-e " + entry.getKey() + "=" + shellEscape(entry.getValue()));
            }
        }
        return flags;
    }

    /**
     * Build a single docker run -e string for embedding in commands.
     */
    public String buildDockerEnvString(Map<String, String> envMap) {
        StringBuilder sb = new StringBuilder();
        for (var entry : envMap.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                String key = entry.getKey().toUpperCase();
                if (key.contains("AWS") && (key.contains("ACCESS") || key.contains("SECRET") || key.contains("KEY"))) {
                    continue;
                }
                sb.append(" -e ").append(entry.getKey()).append("=").append(shellEscape(entry.getValue()));
            }
        }
        return sb.toString().trim();
    }

    /**
     * Apply multi-injection strategy.
     * For each detected dependency, fill in all variant env var names
     * with placeholder values if they are not already present.
     */
    public Map<String, String> applyMultiInjection(
            Map<String, String> envMap,
            List<String> databases,
            List<String> caches
    ) {
        Map<String, String> enriched = new LinkedHashMap<>(envMap);

        List<String> allDeps = new ArrayList<>(databases);
        allDeps.addAll(caches);

        for (String dep : allDeps) {
            List<String> variants = MULTI_INJECT_VARIANTS.get(dep.toLowerCase());
            if (variants == null) continue;

            for (String variant : variants) {
                if (!enriched.containsKey(variant)) {
                    // Set a placeholder — user can override via UI
                    enriched.put(variant, "autopilot_" + variant.toLowerCase() + "_placeholder");
                }
            }
        }

        return enriched;
    }

    private String shellEscape(String value) {
        // Wrap in single quotes to prevent shell interpretation
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
