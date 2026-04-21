package com.autopilot.service.deployment;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class FrontendPatcherService {

    public void patchFrontend(Path projectPath, String basePath, String backendBaseUrl) {
        try {
            System.out.println("Patching frontend at: " + projectPath + " with basePath: " + basePath);
            patchNext(projectPath, basePath);
            patchReact(projectPath, basePath);
            patchVite(projectPath, basePath);
            patchReactRouter(projectPath, basePath);
            patchNginxConf(projectPath, basePath);
            
            // New global patches for monorepo robustness
            patchAbsolutePaths(projectPath, basePath);
            if (backendBaseUrl != null) {
                patchBackendUrls(projectPath, backendBaseUrl);
            }
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

    private void patchVite(Path projectPath, String basePath) throws Exception {
        Path pkg = projectPath.resolve("package.json");
        if (!Files.exists(pkg)) return;

        String content = Files.readString(pkg);
        
        // Ensure trailing slash for Vite base
        String viteBase = basePath;
        if (!viteBase.endsWith("/")) {
            viteBase += "/";
        }

        boolean modified = false;
        if (content.contains("\"vite build\"")) {
            content = content.replace("\"vite build\"", "\"vite build --base=" + viteBase + "\"");
            modified = true;
            System.out.println("Vite config patched successfully via package.json");
        } else if (content.contains("\"tsc -b && vite build\"")) {
            content = content.replace("\"tsc -b && vite build\"", "\"tsc -b && vite build --base=" + viteBase + "\"");
            modified = true;
            System.out.println("Vite (TS) config patched successfully via package.json");
        }

        if (modified) {
            Files.writeString(pkg, content);
        }
    }

    private void patchNginxConf(Path projectPath, String basePath) throws Exception {
        // Find nginx.conf in the project root
        Path nginxConf = projectPath.resolve("nginx.conf");
        if (!Files.exists(nginxConf)) {
            // Check common subdirectories if not in root, or return
            return;
        }

        String content = Files.readString(nginxConf);

        // Ensure trailing slash for rewrite
        String viteBase = basePath;
        if (!viteBase.endsWith("/")) {
            viteBase += "/";
        }

        // We inject a rewrite rule at the beginning of the server block, or location / block
        // to strip the base path transparently inside the container
        if (content.contains("location / {")) {
            content = content.replace("location / {",
                    "location / {\n        rewrite ^" + basePath + "/?(.*)$ /$1 break;\n");
            Files.writeString(nginxConf, content);
            System.out.println("Nginx config patched successfully with rewrite rule");
        }
    }

    private void patchReactRouter(Path projectPath, String basePath) throws Exception {
        // Find typical React entry points
        List<Path> entryPoints = List.of(
            projectPath.resolve("src/App.jsx"),
            projectPath.resolve("src/App.tsx"),
            projectPath.resolve("src/index.jsx"),
            projectPath.resolve("src/index.tsx"),
            projectPath.resolve("src/main.jsx"),
            projectPath.resolve("src/main.tsx")
        );

        String viteBase = basePath;
        if (!viteBase.endsWith("/")) viteBase += "/";

        for (Path entry : entryPoints) {
            if (Files.exists(entry)) {
                String content = Files.readString(entry);
                if (content.contains("react-router-dom")) {
                    String original = content;
                    // Inject basename into Router / BrowserRouter components
                    content = content.replaceAll("<BrowserRouter(\\s+)?(?![^>]*basename)", "<BrowserRouter basename=\"" + basePath + "\"$1");
                    content = content.replaceAll("<Router(\\s+)?(?![^>]*basename)", "<Router basename=\"" + basePath + "\"$1");
                    
                    if (!content.equals(original)) {
                        Files.writeString(entry, content);
                        System.out.println("✅ React Router patched with basename in " + entry.getFileName());
                    }
                }
            }
        }
    }
    private void patchAbsolutePaths(Path projectPath, String basePath) throws Exception {
        if (basePath == null || basePath.equals("/") || basePath.isEmpty()) return;

        String cleanBase = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;

        Files.walk(projectPath)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.endsWith(".jsx") || n.endsWith(".tsx") || n.endsWith(".html") || n.endsWith(".css") || n.endsWith(".js") || n.endsWith(".ts");
                })
                .filter(p -> !p.toString().contains("node_modules"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        String original = content;

                        // Patch src="/..." -> src="basePath/..."
                        // We avoid patching if it already starts with the basePath or is a double slash //
                        content = content.replaceAll("src=\"/(?![/]|" + cleanBase.substring(1) + ")", "src=\"" + cleanBase + "/");
                        content = content.replaceAll("href=\"/(?![/]|" + cleanBase.substring(1) + ")", "href=\"" + cleanBase + "/");
                        
                        // Patch CSS url("/...")
                        content = content.replaceAll("url\\(['\"]?/(?![/]|" + cleanBase.substring(1) + ")", "url(" + cleanBase + "/");

                        if (!content.equals(original)) {
                            Files.writeString(file, content);
                            System.out.println("   🖼️ Patched absolute paths in " + projectPath.relativize(file));
                        }
                    } catch (Exception ignored) {}
                });
    }

    private void patchBackendUrls(Path projectPath, String backendBaseUrl) throws Exception {
        Files.walk(projectPath)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".jsx") || n.endsWith(".tsx") || n.endsWith(".json");
                })
                .filter(p -> !p.toString().contains("node_modules"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        if (content.contains("http://localhost:8080")) {
                            content = content.replace("http://localhost:8080", backendBaseUrl);
                            Files.writeString(file, content);
                            System.out.println("   🔗 Patched backend URL in " + projectPath.relativize(file) + " -> " + backendBaseUrl);
                        }
                    } catch (Exception ignored) {}
                });
    }
}