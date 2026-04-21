package com.autopilot.intelligence.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete output of the Configuration Intelligence Pipeline.
 * Contains every detected config, secret, dependency, and the injection plan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigIntelligenceResult {

    /** All detected configuration entries (secrets + non-secrets) */
    @Builder.Default
    private List<ConfigEntry> entries = new ArrayList<>();

    /** Environment variables detected in source code (process.env.X, os.getenv, etc.) */
    @Builder.Default
    private List<String> referencedEnvVars = new ArrayList<>();

    /** Detected external databases */
    @Builder.Default
    private List<String> databases = new ArrayList<>();

    /** Detected external caches (redis, memcached, etc.) */
    @Builder.Default
    private List<String> caches = new ArrayList<>();

    /** Final normalized environment variable map for injection */
    @Builder.Default
    private Map<String, String> envMap = new HashMap<>();

    /** Docker -e flags ready for injection */
    @Builder.Default
    private List<String> dockerEnvFlags = new ArrayList<>();

    /** Files that were sanitized (secrets replaced with ${VAR} refs) */
    @Builder.Default
    private List<String> sanitizedFiles = new ArrayList<>();

    /** Config files found in the repo */
    @Builder.Default
    private List<String> configFiles = new ArrayList<>();
}
