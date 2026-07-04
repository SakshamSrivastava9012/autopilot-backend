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
            patchViteConfig(projectPath, basePath);
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
        if (content.contains("vite build") && !content.contains("vite build --base=")) {
            content = content.replaceAll("vite build(\\s+[^\"\\n\\r]+)?", "vite build$1 --base=" + viteBase);
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

    private void patchViteConfig(Path projectPath, String basePath) {
        Path[] configs = {
                projectPath.resolve("vite.config.js"),
                projectPath.resolve("vite.config.ts"),
                projectPath.resolve("vite.config.mjs"),
                projectPath.resolve("vite.config.cjs")
        };
        
        String viteBase = basePath;
        if (!viteBase.endsWith("/")) {
            viteBase += "/";
        }

        for (Path configPath : configs) {
            if (Files.exists(configPath)) {
                try {
                    String content = Files.readString(configPath);
                    String original = content;
                    if (content.contains("base:")) {
                        content = content.replaceAll("base:\\s*['\"][^'\"]*['\"]", "base: '" + viteBase + "'");
                    } else if (content.contains("defineConfig({")) {
                        content = content.replace("defineConfig({", "defineConfig({\n  base: '" + viteBase + "',");
                    } else if (content.contains("export default {")) {
                        content = content.replace("export default {", "export default {\n  base: '" + viteBase + "',");
                    }
                    if (!content.equals(original)) {
                        Files.writeString(configPath, content);
                        System.out.println("✅ Patched base path in Vite config file: " + configPath.getFileName());
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Failed to patch Vite config " + configPath.getFileName() + ": " + e.getMessage());
                }
            }
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
                    
                    // Also support createBrowserRouter!
                    if (content.contains("createBrowserRouter")) {
                        // 1. If createBrowserRouter has a second argument containing other options but no basename
                        content = content.replaceAll(
                            "createBrowserRouter\\s*\\(\\s*([^,]+)\\s*,\\s*\\{(?!.*basename)",
                            "createBrowserRouter($1, { basename: \"" + basePath + "\", "
                        );
                        
                        // 2. If createBrowserRouter has a second argument with basename, replace it
                        content = content.replaceAll(
                            "basename\\s*:\\s*['\"][^'\"]*['\"]",
                            "basename: \"" + basePath + "\""
                        );
                        
                        // 3. If createBrowserRouter has only 1 argument (the routes array)
                        content = content.replaceAll(
                            "createBrowserRouter\\s*\\(\\s*([^,)]+)\\s*\\)(?!\\s*\\{)",
                            "createBrowserRouter($1, { basename: \"" + basePath + "\" })"
                        );
                    }
                    
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
        String cleanBaseEscaped = cleanBase.substring(1);

        // 1. Discover all static asset files in the project (under public, static, assets)
        java.util.Set<String> assetPaths = new java.util.HashSet<>();
        try {
            Files.walk(projectPath)
                    .filter(Files::isDirectory)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("public") || name.equals("static") || name.equals("assets");
                    })
                    .forEach(dir -> {
                        try {
                            Files.walk(dir)
                                    .filter(Files::isRegularFile)
                                    .forEach(file -> {
                                        String rel = dir.relativize(file).toString().replace("\\", "/");
                                        // Skip common code/config files in assets to avoid false positive replacements
                                        if (rel.endsWith(".js") || rel.endsWith(".ts") || rel.endsWith(".jsx") || rel.endsWith(".tsx") || rel.endsWith(".html") || rel.endsWith(".css")) {
                                            return;
                                        }
                                        if (!rel.startsWith("/")) rel = "/" + rel;
                                        assetPaths.add(rel);
                                    });
                        } catch (Exception ignored) {}
                    });
        } catch (Exception e) {
            System.out.println("Warning: failed to scan directory for assets: " + e.getMessage());
        }

        System.out.println("   🔍 Discovered " + assetPaths.size() + " static assets for path replacement: " + assetPaths);

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

                        // Patch standard HTML attributes
                        content = content.replaceAll("src=\"/(?![/]|src/|node_modules/|" + cleanBaseEscaped + "|[^\"']+\\.(?:js|ts|jsx|tsx|css)(?:\\?|[\"']))", "src=\"" + cleanBase + "/");
                        content = content.replaceAll("href=\"/(?![/]|src/|node_modules/|" + cleanBaseEscaped + "|[^\"']+\\.(?:js|ts|jsx|tsx|css)(?:\\?|[\"']))", "href=\"" + cleanBase + "/");
                        
                        // Patch CSS url("/...")
                        content = content.replaceAll("url\\((['\"]?)/(?![/]|" + cleanBaseEscaped + ")", "url($1" + cleanBase + "/");

                        // Patch all discovered static asset references in code/templates
                        for (String asset : assetPaths) {
                            String escapedAsset = java.util.regex.Pattern.quote(asset);
                            
                            // Replace in double quotes: "/asset" -> "/basePath/asset"
                            content = content.replaceAll("\"" + escapedAsset + "\"", "\"" + cleanBase + asset + "\"");
                            
                            // Replace in single quotes: '/asset' -> '/basePath/asset'
                            content = content.replaceAll("'" + escapedAsset + "'", "'" + cleanBase + asset + "'");
                            
                            // Replace in template literals: `/asset` -> `/basePath/asset`
                            content = content.replaceAll("`" + escapedAsset + "`", "`" + cleanBase + asset + "`");
                            
                            // Replace in CSS url(/asset)
                            content = content.replaceAll("url\\(" + escapedAsset + "\\)", "url(" + cleanBase + asset + ")");
                            content = content.replaceAll("url\\(\"" + escapedAsset + "\"\\)", "url(\"" + cleanBase + asset + "\")");
                            content = content.replaceAll("url\\('" + escapedAsset + "'\\)", "url('" + cleanBase + asset + "')");
                        }

                        if (!content.equals(original)) {
                            Files.writeString(file, content);
                            System.out.println("   🖼️ Patched absolute paths in " + projectPath.relativize(file));
                        }
                    } catch (Exception ignored) {}
                });
    }

    private void patchBackendUrls(Path projectPath, String backendBaseUrl) throws Exception {
        // All common localhost ports developers use for backend APIs
        List<String> localhostPatterns = List.of(
                "http://localhost:8080",
                "http://localhost:8000",
                "http://localhost:5000",
                "http://localhost:5001",
                "http://localhost:3001",
                "http://localhost:4000",
                "http://localhost:9000",
                "http://localhost:8888",
                "http://127.0.0.1:8080",
                "http://127.0.0.1:8000",
                "http://127.0.0.1:5000",
                "http://127.0.0.1:3001",
                "http://127.0.0.1:4000"
        );

        Files.walk(projectPath)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".jsx")
                            || n.endsWith(".tsx") || n.endsWith(".json") || n.endsWith(".env")
                            || n.endsWith(".env.local") || n.endsWith(".env.production");
                })
                .filter(p -> !p.toString().contains("node_modules"))
                .filter(p -> !p.toString().contains(".git/"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        String original = content;

                        // Replace all known localhost patterns
                        for (String pattern : localhostPatterns) {
                            content = content.replace(pattern, backendBaseUrl);
                        }

                        // Catch fallback patterns like: process.env.API_URL || "http://localhost:8080"
                        content = content.replaceAll(
                                "\\|\\|\\s*[\"']http://localhost:\\d+[^\"']*[\"']",
                                "|| '" + backendBaseUrl + "'"
                        );
                        content = content.replaceAll(
                                "\\|\\|\\s*[\"']http://127\\.0\\.0\\.1:\\d+[^\"']*[\"']",
                                "|| '" + backendBaseUrl + "'"
                        );

                        if (!content.equals(original)) {
                            Files.writeString(file, content);
                            System.out.println("   🔗 Patched backend URLs in " + projectPath.relativize(file));
                        }
                    } catch (Exception ignored) {}
                });
    }

    public void patchBackend(Path projectPath) {
        try {
            Files.walk(projectPath)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    String filename = file.getFileName().toString();
                    try {
                        if (filename.equals("application.properties")) {
                            String content = Files.readString(file);
                            if (!content.contains("server.forward-headers-strategy")) {
                                String separator = content.endsWith("\n") ? "" : "\n";
                                Files.writeString(file, content + separator + "server.forward-headers-strategy=framework\n");
                                System.out.println("✅ Patched application.properties with forward-headers-strategy");
                            }
                        } else if (filename.equals("application.yml") || filename.equals("application.yaml")) {
                            String content = Files.readString(file);
                            if (!content.contains("forward-headers-strategy") && !content.contains("forward_headers_strategy")) {
                                String newContent;
                                if (content.contains("server:")) {
                                    newContent = content.replace("server:", "server:\n  forward-headers-strategy: framework");
                                } else {
                                    String separator = content.endsWith("\n") ? "" : "\n";
                                    newContent = content + separator + "server:\n  forward-headers-strategy: framework\n";
                                }
                                Files.writeString(file, newContent);
                                System.out.println("✅ Patched " + filename + " with forward-headers-strategy");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to patch backend config file " + filename + ": " + e.getMessage());
                    }
                });
        } catch (Exception e) {
            System.err.println("Failed walking workspace for backend patching: " + e.getMessage());
        }
    }
}