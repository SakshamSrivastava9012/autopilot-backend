package com.autopilot.service.deployment.v5.inspection;

import com.autopilot.service.deployment.intelligence.v5.model.RepositoryModelV5;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Infers runtime compatibility behaviour from discovered capabilities.
 * Produces metadata contracts consumed by the reverse proxy and deployment stages.
 * Never patches or modifies application code or assets.
 *
 * @since V5.3 — ADR-006
 */
@Service
public class RuntimeCompatibilityAnalyzer {

    public CompatibilityResult analyze(RepositoryModelV5 model, RuntimeManifestV5 runtimeManifest) {
        System.out.println("🔗 Runtime Compatibility Analyzer — Inferring runtime behaviour...");

        Set<String> capabilities = model.getCapabilities();
        Set<String> frameworks = model.getFrameworks();

        boolean spa = capabilities.contains("SPA");
        boolean ssr = capabilities.contains("SSR");
        boolean websocket = capabilities.contains("WEBSOCKET");
        boolean oauth = capabilities.contains("OAUTH") || capabilities.contains("AUTH");
        boolean staticAssets = capabilities.contains("STATIC_ASSETS");
        boolean dynamicRoutes = capabilities.contains("REST_API") || ssr;

        boolean requiresHistoryFallback = spa && !ssr;
        boolean dynamicImports = ssr || spa; // Modern frameworks use code splitting
        boolean serviceWorker = spa; // SPAs may register service workers
        boolean modulePreload = frameworks.contains("Vite") || frameworks.contains("Next.js");

        CompatibilityContract contract = CompatibilityContract.builder()
                .spaHistoryFallback(requiresHistoryFallback)
                .ssr(ssr)
                .prefixPreservation(true) // Always preserve prefix for multi-tenant deployment
                .websocket(websocket)
                .oauthCallbacks(oauth)
                .imageOptimization(frameworks.contains("Next.js"))
                .staticAssets(staticAssets)
                .dynamicRoutes(dynamicRoutes)
                .requiresBaseTag(spa)
                .requiresHistoryFallback(requiresHistoryFallback)
                .dynamicImports(dynamicImports)
                .serviceWorker(serviceWorker)
                .modulePreload(modulePreload)
                .capabilities(Collections.unmodifiableSet(capabilities))
                .warnings(Collections.emptyList())
                .build();

        RuntimeResolverMetadata resolverMetadata = RuntimeResolverMetadata.builder()
                .basePath("/")
                .assetPrefix("/")
                .apiPrefix("/api")
                .staticAliases(Collections.emptyMap())
                .historyFallback(requiresHistoryFallback)
                .websocketPrefixes(websocket ? Arrays.asList("/ws", "/socket.io") : Collections.emptyList())
                .oauthPrefixes(oauth ? Arrays.asList("/auth", "/oauth", "/callback") : Collections.emptyList())
                .build();

        InspectionReports.CompatibilityReport report = InspectionReports.CompatibilityReport.builder()
                .spaDetected(spa).ssrDetected(ssr).websocketDetected(websocket).oauthDetected(oauth)
                .capabilitiesDetected(capabilities.size())
                .warnings(Collections.emptyList())
                .build();

        System.out.println("   SPA=" + spa + ", SSR=" + ssr + ", WebSocket=" + websocket
                + ", OAuth=" + oauth + ", HistoryFallback=" + requiresHistoryFallback);

        return new CompatibilityResult(contract, resolverMetadata, report);
    }

    @lombok.Value
    public static class CompatibilityResult {
        CompatibilityContract contract;
        RuntimeResolverMetadata resolverMetadata;
        InspectionReports.CompatibilityReport report;
    }
}
