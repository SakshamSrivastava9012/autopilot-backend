package com.autopilot.intelligence;

import com.autopilot.intelligence.model.ConfigEntry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Component 2: Secret Detector
 *
 * Scans configuration files for hardcoded secrets using regex patterns.
 * Language-agnostic — works on raw file content.
 */
@Component
public class SecretDetector {

    /** Patterns that indicate the KEY name is sensitive */
    private static final Pattern SECRET_KEY_PATTERN = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[_-]?key|access[_-]?key|" +
            "auth[_-]?token|private[_-]?key|client[_-]?secret|" +
            "db[_-]?pass|database[_-]?password|jwt[_-]?secret|" +
            "encryption[_-]?key|signing[_-]?key|credentials)"
    );

    /** Patterns that indicate the VALUE is a secret (high-entropy tokens, etc.) */
    private static final List<Pattern> SECRET_VALUE_PATTERNS = List.of(
            // AWS Access Key ID
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            // Generic long hex token (32+ chars)
            Pattern.compile("[a-fA-F0-9]{32,}"),
            // Base64 token (40+ chars)
            Pattern.compile("[A-Za-z0-9+/]{40,}={0,2}"),
            // Bearer token
            Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9._\\-]+"),
            // Connection strings with embedded passwords
            Pattern.compile("(?i)(mysql|postgres|mongodb|redis)://[^\\s]+:[^\\s]+@")
    );

    /**
     * Scan a single config file for secrets.
     * Returns list of ConfigEntry objects marked as secret=true.
     */
    public List<ConfigEntry> detect(Path workspace, String configFile) {
        List<ConfigEntry> secrets = new ArrayList<>();

        Path filePath = workspace.resolve(configFile);
        if (!Files.exists(filePath)) return secrets;

        try {
            List<String> lines = Files.readAllLines(filePath);

            for (String line : lines) {
                String trimmed = line.trim();

                // Skip comments and empty lines
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                    continue;
                }

                // Parse key=value, key: value, "key": "value"
                String[] kv = parseKeyValue(trimmed);
                if (kv == null) continue;

                String key = kv[0];
                String value = kv[1];

                // Skip placeholder values
                if (value.startsWith("${") || value.startsWith("$") ||
                    value.equals("\"\"") || value.equals("''") || value.isBlank()) {
                    continue;
                }

                boolean isSecret = false;

                // Check 1: key name matches secret pattern
                if (SECRET_KEY_PATTERN.matcher(key).find()) {
                    isSecret = true;
                }

                // Check 2: value matches known secret formats
                if (!isSecret) {
                    for (Pattern p : SECRET_VALUE_PATTERNS) {
                        if (p.matcher(value).find()) {
                            isSecret = true;
                            break;
                        }
                    }
                }

                if (isSecret) {
                    secrets.add(ConfigEntry.builder()
                            .key(key)
                            .value(value)
                            .sourceFile(configFile)
                            .secret(true)
                            .build());
                }
            }
        } catch (IOException e) {
            System.err.println("⚠️ SecretDetector: could not read " + configFile + ": " + e.getMessage());
        }

        return secrets;
    }

    /**
     * Parse a line into [key, value] regardless of format.
     * Supports: key=value, key: value, "key": "value", KEY=value
     */
    private String[] parseKeyValue(String line) {
        // Remove surrounding quotes from JSON-style entries
        line = line.replaceAll("^\"", "").replaceAll("\"\\s*,?\\s*$", "");

        // Try = separator
        int eqIdx = line.indexOf('=');
        if (eqIdx > 0) {
            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();
            return new String[]{cleanKey(key), cleanValue(value)};
        }

        // Try : separator (YAML/JSON style)
        int colonIdx = line.indexOf(':');
        if (colonIdx > 0) {
            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();
            if (!value.isEmpty()) {
                return new String[]{cleanKey(key), cleanValue(value)};
            }
        }

        return null;
    }

    private String cleanKey(String key) {
        return key.replaceAll("[\"'`]", "").trim();
    }

    private String cleanValue(String value) {
        return value.replaceAll("[\"'`]", "").replaceAll(",\\s*$", "").trim();
    }
}
