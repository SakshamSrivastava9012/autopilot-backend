package com.autopilot.intelligence;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Component 3: Environment Variable Extractor
 *
 * Detects environment variable USAGE across ALL languages by scanning source code.
 * Uses universal regex patterns — no framework-specific logic.
 */
@Component
public class EnvVariableExtractor {

    /**
     * Universal patterns that match env variable access in any language:
     *
     * JavaScript/TypeScript: process.env.VAR, process.env['VAR'], process.env["VAR"]
     * Python:                os.getenv("VAR"), os.environ["VAR"], os.environ.get("VAR")
     * Java:                  System.getenv("VAR")
     * Go:                    os.Getenv("VAR")
     * Rust:                  env::var("VAR")
     * Ruby:                  ENV["VAR"], ENV.fetch("VAR")
     * PHP:                   getenv("VAR"), $_ENV["VAR"]
     * Shell:                 $VAR, ${VAR}
     * Docker:                ${VAR}, $VAR
     * Config files:          ${VAR}, {{VAR}}, %VAR%
     */
    private static final List<Pattern> ENV_PATTERNS = List.of(
            // process.env.VAR_NAME
            Pattern.compile("process\\.env\\.([A-Z_][A-Z0-9_]+)"),
            // process.env['VAR'] or process.env["VAR"]
            Pattern.compile("process\\.env\\[['\"]([A-Z_][A-Z0-9_]+)['\"]\\]"),
            // os.getenv("VAR") or os.environ["VAR"] or os.environ.get("VAR")
            Pattern.compile("os\\.(?:getenv|environ\\.get|environ)\\(?['\"]([A-Z_][A-Z0-9_]+)['\"]"),
            // System.getenv("VAR")
            Pattern.compile("System\\.getenv\\(['\"]([A-Z_][A-Z0-9_]+)['\"]\\)"),
            // os.Getenv("VAR")
            Pattern.compile("os\\.Getenv\\(['\"]([A-Z_][A-Z0-9_]+)['\"]\\)"),
            // env::var("VAR")
            Pattern.compile("env::var\\(['\"]([A-Z_][A-Z0-9_]+)['\"]\\)"),
            // ENV["VAR"] or ENV.fetch("VAR")
            Pattern.compile("ENV\\[?['\"]([A-Z_][A-Z0-9_]+)['\"]\\]?"),
            // getenv("VAR") or $_ENV["VAR"]
            Pattern.compile("(?:getenv|\\$_ENV)\\(?['\"]([A-Z_][A-Z0-9_]+)['\"]"),
            // ${VAR} in config files, docker-compose, etc.
            Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]+)(?::-[^}]*)?\\}"),
            // @Value("${VAR}") Spring annotation
            Pattern.compile("@Value\\(\"\\$\\{([a-zA-Z_.]+)"),
            // $(VAR) in Makefiles
            Pattern.compile("\\$\\(([A-Z_][A-Z0-9_]+)\\)")
    );

    /** File extensions worth scanning for env var usage */
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".js", ".ts", ".jsx", ".tsx", ".py", ".go", ".rs",
            ".rb", ".php", ".sh", ".bash", ".yml", ".yaml", ".toml",
            ".json", ".properties", ".env", ".cfg", ".conf", ".ini",
            ".dockerfile", ".xml"
    );

    /**
     * Extract all environment variable names referenced in the workspace.
     */
    public Set<String> extract(Path workspace) {
        Set<String> envVars = new LinkedHashSet<>();

        try {
            Files.walk(workspace)
                    .filter(Files::isRegularFile)
                    .filter(this::isSourceFile)
                    .filter(this::notInSkippedDir)
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            for (Pattern pattern : ENV_PATTERNS) {
                                Matcher matcher = pattern.matcher(content);
                                while (matcher.find()) {
                                    String varName = matcher.group(1).toUpperCase()
                                            .replace(".", "_");
                                    envVars.add(varName);
                                }
                            }
                        } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            System.err.println("⚠️ EnvExtractor error: " + e.getMessage());
        }

        System.out.println("🔍 EnvExtractor: found " + envVars.size() + " referenced env vars");
        envVars.forEach(v -> System.out.println("   → " + v));

        return envVars;
    }

    private boolean isSourceFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.equals("dockerfile")) return true;
        return SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private boolean notInSkippedDir(Path path) {
        String full = path.toString();
        return !full.contains("node_modules") && !full.contains(".git/")
                && !full.contains("target/") && !full.contains("__pycache__")
                && !full.contains("/dist/") && !full.contains("/build/");
    }
}
