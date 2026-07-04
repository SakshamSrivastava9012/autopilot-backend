package com.autopilot.service.deployment.v5.runtime.proxy.routing;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Routes OAuth and authentication callbacks (/login, /oauth, /callback, /auth, /auth/*).
 * Never fails on 302/303 redirects.
 *
 * @since V5.4 — ADR-013
 */
@Service
public class OAuthRouter {

    private static final List<String> OAUTH_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "/login", "/oauth", "/callback", "/auth", "/auth/*", "/oauth2/*"
    ));

    public OAuthRoutingTable resolveOAuthRoutes() {
        return OAuthRoutingTable.builder()
                .oauthPrefixes(OAUTH_PREFIXES)
                .allow302Redirects(true)
                .build();
    }

    @Value
    @Builder
    public static class OAuthRoutingTable {
        List<String> oauthPrefixes;
        boolean allow302Redirects;
    }
}
