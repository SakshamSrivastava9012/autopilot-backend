package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Service
public class FrameworkNativeConfiguratorService {

    public void configure(ServiceConfig service, String basePath) {
        if (basePath == null || basePath.equals("/")) {
            return;
        }
        
        Path projectRoot = Path.of(service.getPath());
        if (Files.isRegularFile(projectRoot)) {
            projectRoot = projectRoot.getParent();
        }
        
        String framework = service.getFramework() != null ? service.getFramework().toLowerCase() : "";
        System.out.println("🔧 Attempting native configuration for framework: " + framework + " with basePath: " + basePath);

        try {
            if (framework.contains("next")) {
                configureNextJs(projectRoot, basePath);
            } else if (framework.contains("vite") || framework.contains("react") || framework.contains("vue") || framework.contains("svelte")) {
                configureVite(projectRoot, basePath);
            } else if (framework.contains("nuxt")) {
                configureNuxt(projectRoot, basePath);
            } else if (framework.contains("astro")) {
                configureAstro(projectRoot, basePath);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to inject native configuration for " + framework + ": " + e.getMessage());
        }
    }

    private void configureNextJs(Path root, String basePath) throws IOException {
        Path nextConfig = root.resolve("next.config.js");
        Path nextConfigMjs = root.resolve("next.config.mjs");
        
        String injection = "\n// AUTOPILOT NATIVE CONFIG\n" +
                "const autopilotBasePath = '" + basePath + "';\n";

        if (Files.exists(nextConfig)) {
            String content = Files.readString(nextConfig);
            if (!content.contains("basePath")) {
                if (content.contains("module.exports = {")) {
                    content = content.replace("module.exports = {", "module.exports = {\n  basePath: autopilotBasePath,\n  assetPrefix: autopilotBasePath,");
                    Files.writeString(nextConfig, injection + content);
                } else if (content.contains("const nextConfig = {")) {
                    content = content.replace("const nextConfig = {", "const nextConfig = {\n  basePath: autopilotBasePath,\n  assetPrefix: autopilotBasePath,");
                    Files.writeString(nextConfig, injection + content);
                }
            }
        } else if (Files.exists(nextConfigMjs)) {
            String content = Files.readString(nextConfigMjs);
            if (!content.contains("basePath")) {
                if (content.contains("const nextConfig = {")) {
                    content = content.replace("const nextConfig = {", "const nextConfig = {\n  basePath: autopilotBasePath,\n  assetPrefix: autopilotBasePath,");
                    Files.writeString(nextConfigMjs, injection + content);
                } else if (content.contains("export default {")) {
                    content = content.replace("export default {", "export default {\n  basePath: autopilotBasePath,\n  assetPrefix: autopilotBasePath,");
                    Files.writeString(nextConfigMjs, injection + content);
                }
            }
        }
    }

    private void configureVite(Path root, String basePath) throws IOException {
        Path viteConfig = root.resolve("vite.config.js");
        Path viteConfigTs = root.resolve("vite.config.ts");
        
        String injection = "\n// AUTOPILOT NATIVE CONFIG\n" +
                "const autopilotBase = '" + basePath + "/';\n";

        Path target = Files.exists(viteConfigTs) ? viteConfigTs : (Files.exists(viteConfig) ? viteConfig : null);
        
        if (target != null) {
            String content = Files.readString(target);
            if (!content.contains("base:")) {
                if (content.contains("defineConfig({")) {
                    content = content.replace("defineConfig({", "defineConfig({\n  base: autopilotBase,");
                    Files.writeString(target, injection + content);
                }
            }
        }
    }

    private void configureNuxt(Path root, String basePath) throws IOException {
        Path nuxtConfig = root.resolve("nuxt.config.ts");
        if (!Files.exists(nuxtConfig)) nuxtConfig = root.resolve("nuxt.config.js");
        
        if (Files.exists(nuxtConfig)) {
            String content = Files.readString(nuxtConfig);
            if (!content.contains("baseURL")) {
                if (content.contains("defineNuxtConfig({")) {
                    content = content.replace("defineNuxtConfig({", "defineNuxtConfig({\n  app: { baseURL: '" + basePath + "/' },");
                    Files.writeString(nuxtConfig, content);
                }
            }
        }
    }

    private void configureAstro(Path root, String basePath) throws IOException {
        Path astroConfig = root.resolve("astro.config.mjs");
        if (Files.exists(astroConfig)) {
            String content = Files.readString(astroConfig);
            if (!content.contains("base:")) {
                if (content.contains("defineConfig({")) {
                    content = content.replace("defineConfig({", "defineConfig({\n  base: '" + basePath + "',");
                    Files.writeString(astroConfig, content);
                }
            }
        }
    }
}
