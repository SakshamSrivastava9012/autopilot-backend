package com.autopilot.service.deployment.v5.runtime.proxy.model;

import com.autopilot.service.deployment.v5.runtime.proxy.policy.*;
import com.autopilot.service.deployment.v5.runtime.proxy.routing.*;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Immutable reverse proxy configuration model.
 * Single source of truth for proxy adapters (Nginx, Caddy, Traefik).
 *
 * @since V5.4 — ADR-013
 */
@Value
@Builder
public class ReverseProxyModel {
    String modelId;
    String deploymentId;
    String applicationName;
    RouteResolver.ApplicationRouteTable applicationRouteTable;
    AssetRouter.AssetRoutingTable assetRoutingTable;
    ApiRouter.ApiRoutingTable apiRoutingTable;
    OAuthRouter.OAuthRoutingTable oauthRoutingTable;
    WebSocketRouter.WebSocketRoutingTable webSocketRoutingTable;
    boolean historyFallbackEnabled;
    CompressionPolicy compressionPolicy;
    CachePolicy cachePolicy;
    HeaderPolicy headerPolicy;
    TLSPolicy tlsPolicy;
    Map<String, String> metadata;
}
