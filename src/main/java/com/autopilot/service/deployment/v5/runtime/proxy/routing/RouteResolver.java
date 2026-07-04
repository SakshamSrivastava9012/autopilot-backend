package com.autopilot.service.deployment.v5.runtime.proxy.routing;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Resolves application route tables without hardcoded assumptions.
 *
 * @since V5.4 — ADR-013
 */
@Service
public class RouteResolver {

    public ApplicationRouteTable resolveRoutes(String serviceId, List<String> explicitRoutes) {
        List<String> routes = new ArrayList<>();
        routes.add("/");
        if (explicitRoutes != null) {
            routes.addAll(explicitRoutes);
        }

        return ApplicationRouteTable.builder()
                .serviceId(serviceId)
                .primaryRoute("/")
                .additionalRoutes(routes)
                .build();
    }

    @Value
    @Builder
    public static class ApplicationRouteTable {
        String serviceId;
        String primaryRoute;
        List<String> additionalRoutes;
    }
}
