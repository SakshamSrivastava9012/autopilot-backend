package com.autopilot.analyzer.detectors;

import com.autopilot.analyzer.model.PackageManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DetectorUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static boolean hasFile(List<String> files, String filename) {
        for (String file : files) {
            if (file.endsWith(filename) && !file.contains("node_modules")) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsDependency(Path workspace, List<String> files, String dep) {
        for (String file : files) {
            if (file.endsWith("package.json") && !file.contains("node_modules")) {
                try {
                    Path filePath = workspace.resolve(file);
                    if (!Files.exists(filePath)) {
                        filePath = workspace.resolve(Path.of(file).getFileName());
                    }
                    String content = Files.readString(filePath);
                    if (content.contains("\"" + dep + "\"")) {
                        return true;
                    }
                } catch (IOException ignored) {}
            }
        }
        return false;
    }

    public static String getPackageJsonScript(Path workspace, List<String> files, String scriptName, String defaultValue) {
        for (String file : files) {
            if (file.endsWith("package.json") && !file.contains("node_modules")) {
                try {
                    Path filePath = workspace.resolve(file);
                    if (!Files.exists(filePath)) {
                        filePath = workspace.resolve(Path.of(file).getFileName());
                    }
                    String content = Files.readString(filePath);
                    JsonNode root = objectMapper.readTree(content);
                    JsonNode scripts = root.path("scripts");
                    if (scripts.has(scriptName)) {
                        return scripts.get(scriptName).asText();
                    }
                } catch (Exception ignored) {}
            }
        }
        return defaultValue;
    }

    public static PackageManager detectNodePackageManager(Path workspace, List<String> files) {
        for (String file : files) {
            if (file.endsWith("package-lock.json")) return PackageManager.NPM;
            if (file.endsWith("yarn.lock")) return PackageManager.YARN;
            if (file.endsWith("pnpm-lock.yaml")) return PackageManager.PNPM;
            if (file.endsWith("bun.lockb")) return PackageManager.BUN;
        }
        return PackageManager.NPM; // fallback
    }

    public static String getInstallCommand(PackageManager pm) {
        switch (pm) {
            case YARN: return "yarn install";
            case PNPM: return "pnpm install";
            case BUN: return "bun install";
            default: return "npm install";
        }
    }

    public static String getBuildCommand(PackageManager pm, String packageJsonScript) {
        String base = "";
        switch (pm) {
            case YARN: base = "yarn"; break;
            case PNPM: base = "pnpm"; break;
            case BUN: base = "bun"; break;
            default: base = "npm run"; break;
        }
        if (packageJsonScript != null && !packageJsonScript.isEmpty()) {
            if (pm == PackageManager.NPM && !packageJsonScript.startsWith("npm run")) {
                return "npm run build";
            }
            return base + " build";
        }
        return base + " build";
    }

    public static String deriveServiceName(List<String> files, String defaultName) {
        for (String file : files) {
            if (file.endsWith("package.json") || file.endsWith("pom.xml") || file.endsWith("build.gradle")) {
                Path parent = Path.of(file).getParent();
                if (parent != null && parent.getFileName() != null) {
                    return parent.getFileName().toString();
                }
            }
        }
        return defaultName;
    }
}
