package com.autopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeployedService {
    private String name;
    private String framework;
    private String language;
    private String path;
    private int port;
    private int hostPort;
    private String basePath;
    private String imageUri;
    private String role; // "frontend", "backend", "worker"
    private String buildCommand;
    private String startCommand;
    private String runtimeVersion;
    private String containerName;

    private String healthPath;
    private String protocol;
    private java.util.List<Integer> expectedStatusCodes;
    private int startupTimeout;
    private int retryPolicy;

    // Universal Routing and Asset contracts
    private com.autopilot.analyzer.runtime.RoutingContract routingContract;
    private com.autopilot.analyzer.runtime.AssetContract assetContract;
    private com.autopilot.analyzer.runtime.HealthContract healthContract;
    private com.autopilot.analyzer.runtime.OAuthContract oauthContract;
    private com.autopilot.analyzer.runtime.RuntimeContract runtimeContract;

    public DeployedService(String name, String framework, String language, String path, int port, int hostPort, String basePath, String imageUri, String role, String buildCommand, String startCommand, String runtimeVersion) {
        this.name = name;
        this.framework = framework;
        this.language = language;
        this.path = path;
        this.port = port;
        this.hostPort = hostPort;
        this.basePath = basePath;
        this.imageUri = imageUri;
        this.role = role;
        this.buildCommand = buildCommand;
        this.startCommand = startCommand;
        this.runtimeVersion = runtimeVersion;
        this.healthPath = "/";
        this.protocol = "HTTP";
        this.expectedStatusCodes = java.util.List.of(200, 204, 301, 302, 404);
        this.startupTimeout = 60;
        this.retryPolicy = 20;
    }

    public DeployedService(String name, String framework, String language, String path, int port, int hostPort, String basePath, String imageUri, String role, String buildCommand, String startCommand, String runtimeVersion, String healthPath, String protocol, java.util.List<Integer> expectedStatusCodes, int startupTimeout, int retryPolicy) {
        this.name = name;
        this.framework = framework;
        this.language = language;
        this.path = path;
        this.port = port;
        this.hostPort = hostPort;
        this.basePath = basePath;
        this.imageUri = imageUri;
        this.role = role;
        this.buildCommand = buildCommand;
        this.startCommand = startCommand;
        this.runtimeVersion = runtimeVersion;
        this.healthPath = healthPath;
        this.protocol = protocol;
        this.expectedStatusCodes = expectedStatusCodes;
        this.startupTimeout = startupTimeout;
        this.retryPolicy = retryPolicy;
    }
}
