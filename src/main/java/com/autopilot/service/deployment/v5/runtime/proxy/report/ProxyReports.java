package com.autopilot.service.deployment.v5.runtime.proxy.report;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Dashboard-ready reverse proxy reports.
 *
 * @since V5.4 — ADR-013
 */
public class ProxyReports {

    @Value
    @Builder
    public static class RouteReport {
        String serviceId;
        List<String> applicationRoutes;
        List<String> assetPrefixes;
        List<String> apiPrefixes;
        List<String> oauthPrefixes;
        boolean historyFallbackActive;
    }

    @Value
    @Builder
    public static class ProxyGenerationReport {
        String proxyType;
        boolean generationSuccess;
        boolean reloadSuccess;
        long generationDurationMs;
        List<String> logs;
        List<String> warnings;
    }
}
