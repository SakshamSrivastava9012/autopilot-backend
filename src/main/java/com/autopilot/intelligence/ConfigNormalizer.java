package com.autopilot.intelligence;

import com.autopilot.intelligence.model.ConfigEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Component 5: Config Normalizer
 *
 * Converts detected config entries into standardized environment variable names.
 * Rules:
 * - UPPERCASE everything
 * - Replace dots with underscores
 * - Replace hyphens with underscores
 * - Remove leading/trailing underscores
 * - Prefix with category when ambiguous
 */
@Component
public class ConfigNormalizer {

    /**
     * Normalize a list of ConfigEntry objects.
     * Sets the normalizedKey field on each entry.
     */
    public List<ConfigEntry> normalize(List<ConfigEntry> entries) {
        List<ConfigEntry> normalized = new ArrayList<>();

        for (ConfigEntry entry : entries) {
            String key = normalizeKey(entry.getKey());
            entry.setNormalizedKey(key);
            normalized.add(entry);
        }

        return normalized;
    }

    /**
     * Convert any config key format into a proper ENV_VAR_NAME.
     *
     * Examples:
     *   spring.datasource.url     → SPRING_DATASOURCE_URL
     *   db.password               → DB_PASSWORD
     *   server.port               → SERVER_PORT
     *   database-url              → DATABASE_URL
     *   API_KEY                   → API_KEY (unchanged)
     *   myApp.redis.host          → MYAPP_REDIS_HOST
     */
    public String normalizeKey(String key) {
        if (key == null || key.isBlank()) return "UNKNOWN_KEY";

        return key
                .trim()
                // Remove surrounding quotes
                .replaceAll("^[\"']+|[\"']+$", "")
                // Replace dots, hyphens, spaces with underscores
                .replace('.', '_')
                .replace('-', '_')
                .replace(' ', '_')
                // Remove any non-alphanumeric/underscore characters
                .replaceAll("[^A-Za-z0-9_]", "")
                // Uppercase
                .toUpperCase()
                // Remove leading/trailing underscores
                .replaceAll("^_+|_+$", "")
                // Collapse multiple underscores
                .replaceAll("_+", "_");
    }
}
