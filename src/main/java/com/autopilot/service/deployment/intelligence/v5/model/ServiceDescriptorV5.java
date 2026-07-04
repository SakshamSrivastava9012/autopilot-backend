package com.autopilot.service.deployment.intelligence.v5.model;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Set;

/**
 * Describes a single independently deployable service discovered within a repository.
 * Examples: frontend/, backend/, api/, worker/, apps/web, packages/api
 *
 * @since V5
 */
@Value
@Builder
public class ServiceDescriptorV5 {
    String serviceId;
    String name;
    String root;             // Relative path from repository root (e.g. "backend/")
    String language;
    String framework;        // Informational only
    String role;             // e.g. FRONTEND, BACKEND, WORKER, GATEWAY, ADMIN
    String packageManager;
    String buildSystem;
    String entrypoint;       // e.g. "src/main/java/App.java", "index.js"
    boolean dockerfileExists;
    String dockerfilePath;
    Set<String> capabilities;
    List<String> runtimeHints;
}
