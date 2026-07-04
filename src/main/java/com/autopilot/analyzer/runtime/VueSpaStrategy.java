package com.autopilot.analyzer.runtime;

import java.util.Set;

public class VueSpaStrategy implements FrontendRuntimeStrategy {
    @Override
    public DockerConfiguration docker() {
        return DockerConfiguration.builder()
                .baseImage("node:20-alpine")
                .startCommand("npx serve -s dist -l 3000")
                .port(3000)
                .outputDir("dist")
                .build();
    }

    @Override
    public ReverseProxyConfiguration proxy() {
        return ReverseProxyConfiguration.builder()
                .proxyPassUrl("http://127.0.0.1:3000")
                .rewritePrefix(true)
                .build();
    }

    @Override
    public RoutingContract routing() {
        return RoutingContract.builder()
                .historyFallback(true)
                .preservesPrefix(true)
                .fallbackRedirectPath("/index.html")
                .staticAssetsPrefix("/assets/")
                .build();
    }

    @Override
    public HealthContract health() {
        return HealthContract.builder()
                .checkPath("/")
                .expectedStatusCodes(Set.of(200))
                .expectedMimeTypes(Set.of("text/html"))
                .build();
    }

    @Override
    public AssetContract assets() {
        return AssetContract.builder()
                .staticDirectories(java.util.List.of("dist"))
                .publicDirectories(java.util.List.of("public"))
                .immutableAssetPrefixes(java.util.List.of("/assets/"))
                .requiresPrefixRewrite(true)
                .fileExtensions(Set.of("js", "css", "html", "svg", "png", "jpg", "ico"))
                .build();
    }

    @Override
    public RuntimeCapabilities capabilities() {
        return RuntimeCapabilities.builder()
                .types(Set.of(
                        CapabilityType.SPA,
                        CapabilityType.STATIC_ASSETS,
                        CapabilityType.HISTORY_FALLBACK,
                        CapabilityType.PREFIX_REWRITE_SUPPORTED
                ))
                .build();
    }
}
