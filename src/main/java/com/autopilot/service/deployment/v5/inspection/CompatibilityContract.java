package com.autopilot.service.deployment.v5.inspection;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Set;

/**
 * Immutable compatibility contract describing runtime behaviour inferred from inspection.
 * This metadata is consumed by the reverse proxy and deployment stages — never by patching code.
 *
 * @since V5.3 — ADR-006
 */
@Value
@Builder
public class CompatibilityContract {
    boolean spaHistoryFallback;       // Requires nginx try_files $uri /index.html
    boolean ssr;                       // Server-side rendering active
    boolean prefixPreservation;        // Application expects a base path prefix
    boolean websocket;                 // Application uses WebSocket connections
    boolean oauthCallbacks;            // Application has OAuth callback routes
    boolean imageOptimization;         // Application has image optimization (Next.js, etc.)
    boolean staticAssets;              // Application serves static assets
    boolean dynamicRoutes;             // Application has parameterized routes

    boolean requiresBaseTag;           // HTML <base> tag needed for SPA
    boolean requiresHistoryFallback;   // SPA client-side routing
    boolean dynamicImports;            // Uses dynamic import() for code splitting
    boolean serviceWorker;             // Registers a service worker
    boolean modulePreload;             // Uses <link rel="modulepreload">

    Set<String> capabilities;          // Summarized capability set
    List<String> warnings;
}
