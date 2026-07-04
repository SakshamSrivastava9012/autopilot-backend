package com.autopilot.analyzer.runtime;

import java.util.Set;

public class StaticHtmlStrategy implements FrontendRuntimeStrategy {
    @Override
    public DockerConfiguration docker() {
        return DockerConfiguration.builder()
                .baseImage("nginx:alpine")
                .startCommand("nginx -g 'daemon off;'")
                .port(80)
                .outputDir(".")
                .build();
    }

    @Override
    public ReverseProxyConfiguration proxy() {
        return ReverseProxyConfiguration.builder()
                .proxyPassUrl("http://127.0.0.1:80")
                .rewritePrefix(true)
                .build();
    }

    @Override
    public RoutingContract routing() {
        return RoutingContract.builder()
                .historyFallback(false)
                .preservesPrefix(true)
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
                .staticDirectories(java.util.List.of("."))
                .publicDirectories(java.util.List.of("."))
                .immutableAssetPrefixes(java.util.List.of("/"))
                .requiresPrefixRewrite(true)
                .fileExtensions(Set.of("html", "js", "css", "svg", "png", "jpg", "ico"))
                .build();
    }

    @Override
    public RuntimeCapabilities capabilities() {
        return RuntimeCapabilities.builder()
                .types(Set.of(
                        CapabilityType.STATIC_SITE
                ))
                .build();
    }
}
