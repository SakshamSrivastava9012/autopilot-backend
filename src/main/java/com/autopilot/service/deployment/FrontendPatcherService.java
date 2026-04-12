package com.autopilot.service.deployment;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FrontendPatcherService {

    public void patchFrontend(Path projectPath, String basePath) {
        try {
            System.out.println("Patching frontend at: " + projectPath + " with basePath: " + basePath);
            patchNext(projectPath, basePath);
            patchReact(projectPath, basePath);
        } catch (Exception e) {
            throw new RuntimeException("Frontend patch failed: " + e.getMessage(), e);
        }
    }

    private void patchNext(Path projectPath, String basePath) throws Exception {

        // 🔥 DELETE ALL existing configs (critical fix)
        Path[] allConfigs = {
                projectPath.resolve("next.config.ts"),
                projectPath.resolve("next.config.mjs"),
                projectPath.resolve("next.config.js"),
                projectPath.resolve("next.config.cjs"),
        };

        for (Path cfg : allConfigs) {
            if (Files.exists(cfg)) {
                Files.delete(cfg);
                System.out.println("Deleted existing config: " + cfg.getFileName());
            }
        }

        // ✅ FINAL CORRECT CONFIG (NO assetPrefix)
        Path targetConfig = projectPath.resolve("next.config.js");

        String newConfig =
                "/** @type {import('next').NextConfig} */\n" +
                        "const nextConfig = {\n" +
                        "  basePath: '" + basePath + "',\n" +
                        "  trailingSlash: true,\n" +
                        "  images: { unoptimized: true },\n" +
                        "  eslint: { ignoreDuringBuilds: true },\n" +
                        "  typescript: { ignoreBuildErrors: true }\n" +
                        "};\n\n" +
                        "module.exports = nextConfig;\n";

        Files.writeString(targetConfig, newConfig);

        // ✅ VERIFY
        String written = Files.readString(targetConfig);
        if (!written.contains("basePath: '" + basePath + "'")) {
            throw new RuntimeException("next.config.js patch failed");
        }

        System.out.println("Next.js config patched successfully");
    }

    private void patchReact(Path projectPath, String basePath) throws Exception {
        Path pkg = projectPath.resolve("package.json");
        if (!Files.exists(pkg)) return;

        // Skip for Next.js
        if (Files.exists(projectPath.resolve("next.config.js"))) return;

        String content = Files.readString(pkg);

        if (content.contains("\"homepage\"")) {
            content = content.replaceAll(
                    "\"homepage\"\\s*:\\s*\"[^\"]*\"",
                    "\"homepage\": \"" + basePath + "\""
            );
        } else {
            content = content.replaceFirst(
                    "\\{",
                    "{\n  \"homepage\": \"" + basePath + "\","
            );
        }

        Files.writeString(pkg, content);
    }
}