package com.autopilot.service.deployment.v5.inspection;

import com.autopilot.service.deployment.intelligence.v5.model.RepositoryModelV5;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * The Asset Intelligence Engine.
 *
 * Discovers static assets inside built containers and produces metadata.
 * Never rewrites, patches, or modifies any asset files.
 *
 * @since V5.3 — ADR-006
 */
@Service
public class AssetIntelligenceEngine {

    private static final Map<String, String> MIME_MAP = new LinkedHashMap<>();
    static {
        MIME_MAP.put(".js", "application/javascript");
        MIME_MAP.put(".mjs", "application/javascript");
        MIME_MAP.put(".css", "text/css");
        MIME_MAP.put(".html", "text/html");
        MIME_MAP.put(".json", "application/json");
        MIME_MAP.put(".svg", "image/svg+xml");
        MIME_MAP.put(".png", "image/png");
        MIME_MAP.put(".jpg", "image/jpeg");
        MIME_MAP.put(".gif", "image/gif");
        MIME_MAP.put(".webp", "image/webp");
        MIME_MAP.put(".woff2", "font/woff2");
        MIME_MAP.put(".woff", "font/woff");
        MIME_MAP.put(".ico", "image/x-icon");
        MIME_MAP.put(".map", "application/json");
    }

    /**
     * Analyze assets from the RepositoryModelV5 and produce AssetManifestV5 entries.
     * Pure data transformation — no filesystem access to the built container.
     */
    public AssetIntelligenceResult analyze(RepositoryModelV5 model) {
        System.out.println("📦 Asset Intelligence Engine — Analyzing discovered assets...");

        List<AssetManifestV5> manifests = new ArrayList<>();
        List<String> staticRoots = new ArrayList<>();
        int basePathSensitive = 0;

        for (var asset : model.getAssets()) {
            String path = asset.getPath();
            String containerPath = "/app/" + path;
            String mime = inferMime(path);
            boolean cacheable = isCacheable(path);
            boolean requiresBasePath = isBasePathSensitive(path);
            if (requiresBasePath) basePathSensitive++;

            manifests.add(AssetManifestV5.builder()
                    .logicalPath(path)
                    .containerPath(containerPath)
                    .mimeType(mime)
                    .cacheable(cacheable)
                    .requiresBasePath(requiresBasePath)
                    .generated(isGenerated(path))
                    .runtimeAccessible(true)
                    .build());

            staticRoots.add(containerPath);
        }

        InspectionReports.AssetDiscoveryReport report = InspectionReports.AssetDiscoveryReport.builder()
                .totalAssets(manifests.size())
                .cacheableAssets((int) manifests.stream().filter(AssetManifestV5::isCacheable).count())
                .basePathSensitiveAssets(basePathSensitive)
                .staticRoots(staticRoots)
                .warnings(Collections.emptyList())
                .build();

        System.out.println("   Total: " + manifests.size() + ", Cacheable: " + report.getCacheableAssets()
                + ", BasePath-sensitive: " + basePathSensitive);

        return new AssetIntelligenceResult(Collections.unmodifiableList(manifests), report);
    }

    private String inferMime(String path) {
        for (var entry : MIME_MAP.entrySet()) {
            if (path.endsWith(entry.getKey())) return entry.getValue();
        }
        return "application/octet-stream";
    }

    private boolean isCacheable(String path) {
        return path.contains("static/") || path.contains("_next/") || path.contains("dist/")
                || path.contains("build/") || path.contains("assets/");
    }

    private boolean isBasePathSensitive(String path) {
        return path.contains("_next/") || path.contains("chunk") || path.endsWith(".js") || path.endsWith(".css");
    }

    private boolean isGenerated(String path) {
        return path.contains("dist/") || path.contains("build/") || path.contains("out/")
                || path.contains("_next/");
    }

    @lombok.Value
    public static class AssetIntelligenceResult {
        List<AssetManifestV5> manifests;
        InspectionReports.AssetDiscoveryReport report;
    }
}
