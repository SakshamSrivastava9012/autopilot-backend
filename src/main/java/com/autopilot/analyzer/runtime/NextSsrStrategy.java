package com.autopilot.analyzer.runtime;

import java.util.Set;

public class NextSsrStrategy implements FrontendRuntimeStrategy {
    @Override
    public DockerConfiguration docker() {
        return DockerConfiguration.builder()
                .baseImage("node:20-alpine")
                .startCommand("npm run start")
                .port(3000)
                .outputDir(".next")
                .build();
    }

    @Override
    public ReverseProxyConfiguration proxy() {
        return ReverseProxyConfiguration.builder()
                // IMPORTANT: Next.js handles basePath internally. Host Nginx must forward the request
                // with the prefix (do not append trailing slash to upstream proxy_pass).
                .proxyPassUrl("http://127.0.0.1:3000")
                .rewritePrefix(false) 
                .build();
    }

    @Override
    public RoutingContract routing() {
        return RoutingContract.builder()
                .historyFallback(false)
                .preservesPrefix(true)
                .staticAssetsPrefix("/_next/")
                .build();
    }

    @Override
    public HealthContract health() {
        return HealthContract.builder()
                .checkPath("/")
                .expectedStatusCodes(Set.of(200, 307, 308))
                .expectedMimeTypes(Set.of("text/html", "application/json"))
                .build();
    }

    @Override
    public AssetContract assets() {
        return AssetContract.builder()
                .staticDirectories(java.util.List.of(".next"))
                .publicDirectories(java.util.List.of("public"))
                .immutableAssetPrefixes(java.util.List.of("/_next/static/", "/_next/image/", "/public/", "/favicon.ico", "/robots.txt"))
                .requiresPrefixRewrite(false)
                .fileExtensions(Set.of("js", "css", "html", "svg", "png", "jpg"))
                .build();
    }

    @Override
    public RuntimeCapabilities capabilities() {
        return RuntimeCapabilities.builder()
                .types(Set.of(
                        CapabilityType.SSR,
                        CapabilityType.NODE_SERVER,
                        CapabilityType.STATIC_ASSETS,
                        CapabilityType.BASE_PATH_REQUIRED
                ))
                .build();
    }
}
