package com.autopilot.service.deployment.intelligence;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Set;

/**
 * An immutable representation of a repository's structure and capabilities.
 * Produced by the RepositoryIntelligenceEngine.
 * No components should scan the filesystem after this model is built.
 */
@Data
@Builder
public class RepositoryModel {
    // Versioning and Caching Primitives
    private String schemaVersion;
    private long generatedAt;
    private String engineVersion;
    private java.util.Map<String, String> detectorVersions;
    private String repositoryHash;

    private String repositoryPath;
    
    // Core capabilities (e.g. SPA, JAVA_SERVER, REST_API)
    private Set<String> capabilities;
    
    // Informational metadata
    private String primaryLanguage;
    private String detectedFramework; // Informational only! Not for deployment logic.
    private String packageManager;
    private String buildTool;
    
    // Discovered structural elements
    private boolean hasDocker;
    private boolean hasCompose;
    private boolean hasHelm;
    private boolean hasTerraform;
    
    // Deep structural discoveries
    private List<String> staticAssetDirectories;
    private List<String> databaseDependencies;
    private List<String> healthEndpoints;
    private List<String> oauthEndpoints;
    private List<Integer> exposedPorts;
    private List<String> runtimeHints;
}
