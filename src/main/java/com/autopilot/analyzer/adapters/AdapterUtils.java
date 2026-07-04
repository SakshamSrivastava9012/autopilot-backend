package com.autopilot.analyzer.adapters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AdapterUtils {
    public static boolean containsDependency(Path workspace, List<String> files, String dep) {
        for (String file : files) {
            if (file.endsWith("package.json") && !file.contains("node_modules")) {
                try {
                    String content = Files.readString(workspace.resolve(file));
                    if (content.contains("\"" + dep + "\"")) {
                        return true;
                    }
                } catch (IOException ignored) {}
            }
        }
        return false;
    }

    public static String detectPackageManager(Path workspace, List<String> files) {
        for (String file : files) {
            if (file.endsWith("package-lock.json")) return "npm";
            if (file.endsWith("yarn.lock")) return "yarn";
            if (file.endsWith("pnpm-lock.yaml")) return "pnpm";
            if (file.endsWith("bun.lockb")) return "bun";
        }
        return "npm"; // fallback
    }

    public static String getInstallCommand(String packageManager) {
        switch (packageManager) {
            case "yarn": return "yarn install";
            case "pnpm": return "pnpm install";
            case "bun": return "bun install";
            default: return "npm install";
        }
    }
}
