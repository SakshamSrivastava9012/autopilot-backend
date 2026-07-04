package com.autopilot.service.deployment.v5.runtime.proxy.engine;

import com.autopilot.service.deployment.v5.runtime.proxy.adapter.ReverseProxyAdapter;
import com.autopilot.service.deployment.v5.runtime.proxy.model.ReverseProxyModel;
import com.autopilot.service.deployment.v5.runtime.proxy.policy.*;
import com.autopilot.service.deployment.v5.runtime.proxy.report.ProxyReports;
import com.autopilot.service.deployment.v5.runtime.proxy.routing.*;
import com.autopilot.service.deployment.v5.runtime.proxy.snapshot.ReverseProxySnapshot;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Universal Reverse Proxy Engine V5.
 * Sole component responsible for exposing runtime services to end users.
 *
 * Proxy adapts to applications. Applications are immutable after build.
 * NO asset rewriting. NO JavaScript patching. NO HTML rewriting. NO script injection.
 *
 * @since V5.4 — ADR-013
 */
@Service
public class ReverseProxyEngineV5 {

    private final RouteResolver routeResolver;
    private final AssetRouter assetRouter;
    private final ApiRouter apiRouter;
    private final OAuthRouter oauthRouter;
    private final WebSocketRouter webSocketRouter;
    private final HistoryFallbackRouter historyFallbackRouter;
    private final List<ReverseProxyAdapter> adapters;

    public ReverseProxyEngineV5(RouteResolver routeResolver,
                               AssetRouter assetRouter,
                               ApiRouter apiRouter,
                               OAuthRouter oauthRouter,
                               WebSocketRouter webSocketRouter,
                               HistoryFallbackRouter historyFallbackRouter,
                               List<ReverseProxyAdapter> adapters) {
        this.routeResolver = routeResolver;
        this.assetRouter = assetRouter;
        this.apiRouter = apiRouter;
        this.oauthRouter = oauthRouter;
        this.webSocketRouter = webSocketRouter;
        this.historyFallbackRouter = historyFallbackRouter;
        this.adapters = adapters != null ? adapters : Collections.emptyList();
    }

    public EngineResult generateProxyConfig(String deploymentId, String framework, String proxyType) {
        long start = System.currentTimeMillis();
        String targetProxy = proxyType != null ? proxyType : "NGINX";
        System.out.println("🔀 Reverse Proxy Engine V5 — Generating " + targetProxy + " routing configuration for deployment [" + deploymentId + "]...");

        // 1. Resolve Routers & Policies
        RouteResolver.ApplicationRouteTable appRoutes = routeResolver.resolveRoutes(deploymentId, Collections.emptyList());
        AssetRouter.AssetRoutingTable assetRoutes = assetRouter.resolveAssetRoutes();
        ApiRouter.ApiRoutingTable apiRoutes = apiRouter.resolveApiRoutes(framework);
        OAuthRouter.OAuthRoutingTable oauthRoutes = oauthRouter.resolveOAuthRoutes();
        WebSocketRouter.WebSocketRoutingTable wsRoutes = webSocketRouter.resolveWebSocketRoutes(framework);
        boolean historyFallback = historyFallbackRouter.isHistoryFallbackEnabled(framework);

        CompressionPolicy compression = CompressionPolicy.builder().gzipEnabled(true).brotliEnabled(false).gzipLevel(6).build();
        CachePolicy cache = CachePolicy.builder().immutableAssetsControl("public, max-age=31536000, immutable").dynamicApiControl("no-cache").etagEnabled(true).build();
        HeaderPolicy header = HeaderPolicy.builder().hstsEnabled(true).hstsDirective("max-age=31536000").corsEnabled(true).allowOrigin("*").xForwardedHeadersEnabled(true).build();
        TLSPolicy tls = TLSPolicy.builder().tlsEnabled(false).forceHttpsRedirect(false).build();

        // 2. Build ReverseProxyModel
        ReverseProxyModel model = ReverseProxyModel.builder()
                .modelId("proxy-model-" + UUID.randomUUID().toString().substring(0, 8))
                .deploymentId(deploymentId)
                .applicationName(deploymentId)
                .applicationRouteTable(appRoutes)
                .assetRoutingTable(assetRoutes)
                .apiRoutingTable(apiRoutes)
                .oauthRoutingTable(oauthRoutes)
                .webSocketRoutingTable(wsRoutes)
                .historyFallbackEnabled(historyFallback)
                .compressionPolicy(compression)
                .cachePolicy(cache)
                .headerPolicy(header)
                .tlsPolicy(tls)
                .metadata(Collections.singletonMap("framework", framework != null ? framework : "GENERIC"))
                .build();

        // 3. Select Adapter & Generate Config
        ReverseProxyAdapter selectedAdapter = adapters.stream()
                .filter(a -> a.supports(targetProxy))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No reverse proxy adapter found for proxy type: " + targetProxy));

        String config = selectedAdapter.generateConfig(model);
        boolean configVerified = selectedAdapter.verifyConfig(config);
        boolean reloadSuccess = selectedAdapter.reload(targetProxy);

        long duration = System.currentTimeMillis() - start;

        // 4. Produce Snapshot & Reports
        List<String> activeRoutes = new ArrayList<>();
        activeRoutes.addAll(appRoutes.getAdditionalRoutes());
        activeRoutes.addAll(apiRoutes.getApiPrefixes());

        ReverseProxySnapshot snapshot = ReverseProxySnapshot.builder()
                .deploymentId(deploymentId)
                .proxyType(targetProxy)
                .generatedConfig(config)
                .proxyModel(model)
                .configVerified(configVerified)
                .reloadSuccessful(reloadSuccess)
                .activeRoutes(activeRoutes)
                .metadata(Collections.singletonMap("adapter", selectedAdapter.getClass().getSimpleName()))
                .build();

        ProxyReports.ProxyGenerationReport report = ProxyReports.ProxyGenerationReport.builder()
                .proxyType(targetProxy)
                .generationSuccess(configVerified)
                .reloadSuccess(reloadSuccess)
                .generationDurationMs(duration)
                .logs(Collections.singletonList("Reverse proxy generated cleanly for adapter: " + selectedAdapter.getClass().getSimpleName()))
                .warnings(Collections.emptyList())
                .build();

        return new EngineResult(model, config, snapshot, report);
    }

    @lombok.Value
    public static class EngineResult {
        ReverseProxyModel model;
        String generatedConfig;
        ReverseProxySnapshot snapshot;
        ProxyReports.ProxyGenerationReport report;
    }
}
