package com.autopilot.intelligence;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Component 1: Universal Config Scanner
 *
 * Recursively scans ANY repository and identifies configuration files.
 * Language-agnostic — uses file extension and naming patterns only.
 */
@Component
public class UniversalConfigScanner {

    /** File extensions that are always configuration files */
    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
            ".properties", ".env", ".yaml", ".yml", ".json",
            ".toml", ".ini", ".cfg", ".conf", ".config"
    );

    /** File name patterns (without extension) that indicate config files */
    private static final Set<String> CONFIG_NAME_PATTERNS = Set.of(
            ".env", ".env.local", ".env.production", ".env.development", ".env.staging",
            "dockerfile", "docker-compose", "makefile", "procfile",
            "netlify.toml", "vercel.json", "fly.toml", "render.yaml"
    );

    /** Files to explicitly ignore */
    private static final Set<String> IGNORED_FILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            "pom.xml", "build.gradle", "build.gradle.kts"
    );

    /** Directories to skip (irrelevant to config detection) */
    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "target", "build", "dist",
            "__pycache__", ".idea", ".vscode", "vendor", ".gradle"
    );

    /**
     * Scan the workspace and return all config file paths (relative to workspace).
     */
    public List<String> scan(Path workspace) {
        List<String> configFiles = new ArrayList<>();

        try {
            Files.walk(workspace)
                    .filter(Files::isRegularFile)
                    .filter(this::notInSkippedDir)
                    .forEach(path -> {
                        String relative = workspace.relativize(path).toString();
                        if (isConfigFile(relative)) {
                            configFiles.add(relative);
                        }
                    });
        } catch (IOException e) {
            System.err.println("⚠️ ConfigScanner error: " + e.getMessage());
        }

        System.out.println("📁 ConfigScanner: found " + configFiles.size() + " config files");
        configFiles.forEach(f -> System.out.println("   → " + f));

        return configFiles;
    }

    private boolean isConfigFile(String relativePath) {
        String lower = relativePath.toLowerCase();
        String fileName = Path.of(lower).getFileName().toString();

        if (IGNORED_FILES.contains(fileName)) {
            return false;
        }

        // Check exact name patterns
        for (String pattern : CONFIG_NAME_PATTERNS) {
            if (fileName.equals(pattern) || fileName.startsWith(pattern + ".")) {
                return true;
            }
        }

        // Check extensions
        for (String ext : CONFIG_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }

        // Special cases: config in the path name
        if (lower.contains("/config/") || lower.contains("/settings/")) {
            return true;
        }

        return false;
    }

    private boolean notInSkippedDir(Path path) {
        for (Path component : path) {
            if (SKIP_DIRS.contains(component.toString().toLowerCase())) {
                return false;
            }
        }
        return true;
    }
}
