package com.autopilot.service.deployment.v5.runtime.startup.negotiation;

import java.util.List;

public enum StartupProfile {
    SPRING_BOOT(
        List.of("/actuator/health", "/"), 
        List.of("/actuator/health", "/"), 
        8080, 
        60000L, 
        60000L, 
        "SPRING_BOOT_STARTUP_STRATEGY", 
        "Container running -> JVM running -> Port bound -> HTTP probe -> Actuator if available -> READY"
    ),
    JVM(
        List.of("/health", "/"), 
        List.of("/health", "/"), 
        8080, 
        60000L, 
        60000L, 
        "JVM_STARTUP_STRATEGY", 
        "Container running -> JVM running -> Port bound -> HTTP probe -> READY"
    ),
    NODE_SERVER(
        List.of("/health", "/"), 
        List.of("/health", "/"), 
        3000, 
        30000L, 
        30000L, 
        "NODE_SERVER_STARTUP_STRATEGY", 
        "Container running -> Port bound -> HTTP probe -> READY"
    ),
    NGINX_STATIC(
        List.of("/", "/index.html"), 
        List.of("/", "/index.html"), 
        80, 
        15000L, 
        15000L, 
        "NGINX_STATIC_STARTUP_STRATEGY", 
        "Container running -> nginx master process running -> Port 80 listening -> GET / -> GET index.html -> READY"
    ),
    STATIC_SITE(
        List.of("/"), 
        List.of("/"), 
        80, 
        10000L, 
        10000L, 
        "STATIC_SITE_STARTUP_STRATEGY", 
        "HTTP probe only -> READY"
    ),
    PYTHON(
        List.of("/health", "/"), 
        List.of("/health", "/"), 
        8000, 
        30000L, 
        30000L, 
        "PYTHON_STARTUP_STRATEGY", 
        "Container running -> Port bound -> HTTP probe -> READY"
    ),
    GO(
        List.of("/healthz", "/"), 
        List.of("/healthz", "/"), 
        8080, 
        15000L, 
        15000L, 
        "GO_STARTUP_STRATEGY", 
        "Container running -> Port bound -> HTTP probe -> READY"
    ),
    RUST(
        List.of("/healthz", "/"), 
        List.of("/healthz", "/"), 
        8080, 
        15000L, 
        15000L, 
        "RUST_STARTUP_STRATEGY", 
        "Container running -> Port bound -> HTTP probe -> READY"
    );

    private final List<String> readinessEndpoints;
    private final List<String> healthEndpoints;
    private final int defaultPort;
    private final long defaultReadinessTimeoutMs;
    private final long defaultHealthTimeoutMs;
    private final String strategyName;
    private final String successCriteria;

    StartupProfile(List<String> readinessEndpoints, List<String> healthEndpoints, int defaultPort,
                   long defaultReadinessTimeoutMs, long defaultHealthTimeoutMs, String strategyName, String successCriteria) {
        this.readinessEndpoints = readinessEndpoints;
        this.healthEndpoints = healthEndpoints;
        this.defaultPort = defaultPort;
        this.defaultReadinessTimeoutMs = defaultReadinessTimeoutMs;
        this.defaultHealthTimeoutMs = defaultHealthTimeoutMs;
        this.strategyName = strategyName;
        this.successCriteria = successCriteria;
    }

    public List<String> getReadinessEndpoints() { return readinessEndpoints; }
    public List<String> getHealthEndpoints() { return healthEndpoints; }
    public int getDefaultPort() { return defaultPort; }
    public long getDefaultReadinessTimeoutMs() { return defaultReadinessTimeoutMs; }
    public long getDefaultHealthTimeoutMs() { return defaultHealthTimeoutMs; }
    public String getStrategyName() { return strategyName; }
    public String getSuccessCriteria() { return successCriteria; }
}
