package com.autopilot.service.deployment.v5.runtime.proxy.routing;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Routes WebSocket connections (Upgrade, Connection, HTTP/2, HTTP/3, SSE).
 *
 * @since V5.4 — ADR-013
 */
@Service
public class WebSocketRouter {

    public WebSocketRoutingTable resolveWebSocketRoutes(String framework) {
        List<String> wsPrefixes = new ArrayList<>();
        wsPrefixes.add("/ws");
        wsPrefixes.add("/socket.io");
        wsPrefixes.add("/subscriptions");

        return WebSocketRoutingTable.builder()
                .wsPrefixes(wsPrefixes)
                .upgradeConnectionHeaders(true)
                .build();
    }

    @Value
    @Builder
    public static class WebSocketRoutingTable {
        List<String> wsPrefixes;
        boolean upgradeConnectionHeaders;
    }
}
