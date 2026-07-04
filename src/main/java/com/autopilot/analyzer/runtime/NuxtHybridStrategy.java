package com.autopilot.analyzer.runtime;

import java.util.Set;

public class NuxtHybridStrategy implements FrontendRuntimeStrategy {
    @Override
    public DockerConfiguration docker() {
        return DockerConfiguration.builder()
                .baseImage("node:20-alpine")
                .startCommand("node .output/server/index.mjs")
                .port(3000)
                .outputDir(".output")
                .build();
    }

    @Override
    public ReverseProxyConfiguration proxy() {
        return ReverseProxyConfiguration.builder()
                .proxyPassUrl("http://127.0.0.1:3000")
                .rewritePrefix(false) // Nuxt handles basePath configuration internally
                .build();
    }

    @Override
    public RoutingContract routing() {
        return RoutingContract.builder()
                .historyFallback(false)
                .preservesPrefix(true)
                .staticAssetsPrefix("/_nuxt/")
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
                .staticDirectories(java.util.List.of(".output"))
                .publicDirectories(java.util.List.of("public"))
                .immutableAssetPrefixes(java.util.List.of("/_nuxt/"))
                .requiresPrefixRewrite(false)
                .fileExtensions(Set.of("js", "css", "html", "svg", "png", "jpg"))
                .build();
    }

    @Override
    public RuntimeCapabilities capabilities() {
        return RuntimeCapabilities.builder()
                .types(Set.of(
                        CapabilityType.HYBRID,
                        CapabilityType.NODE_SERVER,
                        CapabilityType.STATIC_ASSETS,
                        CapabilityType.BASE_PATH_REQUIRED
                ))
                .build();
    }
}
