package com.autopilot.intelligence;

import com.autopilot.intelligence.model.ConfigEntry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Component 6: Config Sanitizer
 *
 * Replaces hardcoded secret values in config files with environment variable references.
 * Preserves original file syntax (properties, YAML, JSON, etc.).
 *
 * Example:
 *   BEFORE: db.password=root123
 *   AFTER:  db.password=${DB_PASSWORD}
 */
@Component
public class ConfigSanitizer {

    /**
     * For each secret entry, replace the hardcoded value with an env var reference
     * in the source file. Returns list of files that were modified.
     */
    public List<String> sanitize(Path workspace, List<ConfigEntry> secrets) {
        List<String> sanitizedFiles = new ArrayList<>();

        for (ConfigEntry secret : secrets) {
            if (!secret.isSecret()) continue;
            if (secret.getValue() == null || secret.getValue().isBlank()) continue;
            if (secret.getNormalizedKey() == null) continue;

            Path filePath = workspace.resolve(secret.getSourceFile());
            if (!Files.exists(filePath)) continue;

            try {
                String content = Files.readString(filePath);
                String original = content;

                String upperKey = secret.getKey().toUpperCase();
                boolean isAwsCred = upperKey.contains("AWS") && (upperKey.contains("ACCESS") || upperKey.contains("SECRET") || upperKey.contains("KEY"));

                if (isAwsCred) {
                    // 💥 Completely eliminate AWS credentials from config files!
                    // This forces SDKs to fall back to the secure IAM Instance Profile 
                    // instead of throwing an Unresolvable Placeholder exception.
                    content = content.replaceAll("(?mi)^.*" + java.util.regex.Pattern.quote(secret.getKey()) + ".*$(\\r?\\n)?", "");
                } else {
                    // Build the replacement reference based on file type
                    String envRef = buildEnvReference(secret.getSourceFile(), secret.getNormalizedKey());

                    // Replace the hardcoded value with the env var reference
                    // Be careful to only replace the VALUE, not the key
                    content = content.replace(
                            secret.getKey() + "=" + secret.getValue(),
                            secret.getKey() + "=" + envRef
                    );
                    content = content.replace(
                            secret.getKey() + ": " + secret.getValue(),
                            secret.getKey() + ": " + envRef
                    );
                    // JSON format: "key": "value"
                    content = content.replace(
                            "\"" + secret.getKey() + "\": \"" + secret.getValue() + "\"",
                            "\"" + secret.getKey() + "\": \"" + envRef + "\""
                    );
                }

                if (!content.equals(original)) {
                    Files.writeString(filePath, content);
                    sanitizedFiles.add(secret.getSourceFile());
                    System.out.println("🔒 Sanitized: " + secret.getKey() + " in " + secret.getSourceFile());
                }

            } catch (IOException e) {
                System.err.println("⚠️ ConfigSanitizer: could not sanitize " + secret.getSourceFile() + ": " + e.getMessage());
            }
        }

        return sanitizedFiles;
    }

    /**
     * Generate the appropriate environment variable reference syntax based on file type.
     */
    private String buildEnvReference(String fileName, String envVarName) {
        String lower = fileName.toLowerCase();

        if (lower.endsWith(".properties") || lower.endsWith(".env") || lower.endsWith(".ini") || lower.endsWith(".cfg")) {
            return "${" + envVarName + "}";
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "${" + envVarName + "}";
        }
        if (lower.endsWith(".json")) {
            return "${" + envVarName + "}";
        }
        if (lower.endsWith(".toml")) {
            return "${" + envVarName + "}";
        }

        // Default: shell-style reference
        return "${" + envVarName + "}";
    }
}
