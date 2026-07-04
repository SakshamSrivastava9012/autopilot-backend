package com.autopilot.service.deployment.v5.inspection;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;

/**
 * Immutable metadata describing how the reverse proxy / runtime should resolve paths.
 * Consumed by Milestone 4 (deployment) — never modifies application code or assets.
 *
 * @since V5.3 — ADR-006
 */
@Value
@Builder
public class RuntimeResolverMetadata {
    String basePath;                  // e.g. "/app-xxxx"
    String assetPrefix;               // e.g. "/app-xxxx/_next/static"
    String apiPrefix;                 // e.g. "/app-xxxx-api"
    Map<String, String> staticAliases;  // e.g. { "/static" -> "/app/build/static" }
    boolean historyFallback;
    List<String> websocketPrefixes;
    List<String> oauthPrefixes;
}
