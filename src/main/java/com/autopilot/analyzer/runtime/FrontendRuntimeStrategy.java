package com.autopilot.analyzer.runtime;

public interface FrontendRuntimeStrategy {
    DockerConfiguration docker();
    ReverseProxyConfiguration proxy();
    RoutingContract routing();
    HealthContract health();
    AssetContract assets();
    RuntimeCapabilities capabilities();
}
