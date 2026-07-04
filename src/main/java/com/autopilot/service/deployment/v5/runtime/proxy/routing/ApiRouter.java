package com.autopilot.service.deployment.v5.runtime.proxy.routing;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Routes API endpoints (REST, GraphQL, gRPC-web, SSE).
 *
 * @since V5.4 — ADR-013
 */
@Service
public class ApiRouter {

    public ApiRoutingTable resolveApiRoutes(String framework) {
        List<String> apiPrefixes = new ArrayList<>();
        if (framework != null && framework.toLowerCase().contains("spring")) {
            apiPrefixes.add("/api");
            apiPrefixes.add("/actuator");
        } else {
            apiPrefixes.add("/api");
            apiPrefixes.add("/graphql");
        }

        return ApiRoutingTable.builder()
                .apiPrefixes(apiPrefixes)
                .supportsGrpcWeb(false)
                .supportsSse(true)
                .build();
    }

    @Value
    @Builder
    public static class ApiRoutingTable {
        List<String> apiPrefixes;
        boolean supportsGrpcWeb;
        boolean supportsSse;
    }
}
