package com.autopilot.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceConfig {

    private String name;
    @EqualsAndHashCode.Include
    private String serviceId;

    private String serviceRoot; // absolute path
    private String repositoryRoot;
    private String dockerContext;
    private String dockerfileLocation;

    private String framework;
    private String path; // absolute path, equal to serviceRoot
    private String buildContext;
    private List<String> expectedManifestFiles;
    private String dockerStrategy;
    private String validatorStrategy;

    private String buildCommand;
    private String startCommand;
    private Integer port;
    private boolean dockerfileExists;

    // Database detection
    private String requiresDatabase; // e.g., "POSTGRES", "MYSQL", "MONGO"
    private String databaseEnvVarName; // e.g., "DATABASE_URL" or "SPRING_DATASOURCE_URL"

    // Failsafe Metadata
    private String strategyUsed;   // DOCKERFILE, TEMPLATE, AI_GENERATED, FALLBACK
    private String language;       // java, javascript, python, go, rust
    private String runtimeVersion; // e.g. "21", "20", "3.10"
    private String runtime;
    private String packageManager;
    private String artifactLocation;

    // Confidence scoring (0-100)
    private Integer confidence;

    // Detected environment variables
    private List<String> envVariables;

    // Build steps for multi-step builds
    private List<String> buildSteps;

    // Generated deployment manifest
    private DeploymentManifest deploymentManifest;

    public String getServiceId() {
        return serviceId != null ? serviceId : name;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
        if (this.name == null) {
            this.name = serviceId;
        }
    }

    public String getServiceRoot() {
        return serviceRoot != null ? serviceRoot : path;
    }

    public void setServiceRoot(String serviceRoot) {
        this.serviceRoot = serviceRoot;
        this.path = serviceRoot;
        this.dockerContext = serviceRoot;
    }

    private String basePath;
    private String role; // e.g. frontend, backend, worker, api, etc.

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void validate() {
        java.util.Objects.requireNonNull(name, "service name cannot be null");
        java.util.Objects.requireNonNull(serviceId, "serviceId cannot be null");
        java.util.Objects.requireNonNull(path, "path cannot be null");
        java.util.Objects.requireNonNull(framework, "framework cannot be null");
        if (name.isBlank()) throw new IllegalStateException("Service name cannot be blank");
        if (serviceId.isBlank()) throw new IllegalStateException("Service ID cannot be blank");
        if (path.isBlank()) throw new IllegalStateException("Service path cannot be blank");
        if (framework.isBlank()) throw new IllegalStateException("Service framework cannot be blank");
    }
}