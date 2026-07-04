package com.autopilot.service.deployment.v5.runtime.proxy.routing;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Routes static assets without scanning repository or rewriting assets.
 *
 * @since V5.4 — ADR-013
 */
@Service
public class AssetRouter {

    private static final List<String> DEFAULT_ASSET_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "/assets", "/static", "/public", "/_next", "/dist", "/build", "/images", "/favicon.ico"
    ));

    public AssetRoutingTable resolveAssetRoutes() {
        return AssetRoutingTable.builder()
                .assetPrefixes(DEFAULT_ASSET_PREFIXES)
                .cacheImmutable(true)
                .build();
    }

    @Value
    @Builder
    public static class AssetRoutingTable {
        List<String> assetPrefixes;
        boolean cacheImmutable;
    }
}
