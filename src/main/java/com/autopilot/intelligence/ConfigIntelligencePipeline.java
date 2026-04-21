package com.autopilot.intelligence;

import com.autopilot.intelligence.model.ConfigEntry;
import com.autopilot.intelligence.model.ConfigIntelligenceResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

/**
 * Component 8: Configuration Intelligence Pipeline
 *
 * Orchestrates ALL config intelligence components in sequence:
 *
 *   Repo → Scanner → SecretDetector → EnvExtractor → DependencyDetector
 *        → Normalizer → Sanitizer → InjectionEngine → Result
 *
 * This is the SINGLE entry point for the deployment pipeline.
 * Call analyze(workspace) and get everything you need.
 */
@Service
@RequiredArgsConstructor
public class ConfigIntelligencePipeline {

    private final UniversalConfigScanner configScanner;
    private final SecretDetector secretDetector;
    private final EnvVariableExtractor envExtractor;
    private final DependencyDetector dependencyDetector;
    private final ConfigNormalizer normalizer;
    private final ConfigSanitizer sanitizer;
    private final EnvInjectionEngine injectionEngine;

    /**
     * Run the full Configuration Intelligence Pipeline on a workspace.
     *
     * @param workspace Path to the cloned repository
     * @return ConfigIntelligenceResult with all detected configs, secrets, deps, and injection plan
     */
    public ConfigIntelligenceResult analyze(Path workspace) {

        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  🧠 CONFIG INTELLIGENCE PIPELINE — START");
        System.out.println("═══════════════════════════════════════════════════════");

        ConfigIntelligenceResult result = new ConfigIntelligenceResult();

        // ── STEP 1: Scan for config files ────────────────────────────────
        System.out.println("\n── STEP 1: Scanning for config files ──");
        List<String> configFiles = configScanner.scan(workspace);
        result.setConfigFiles(configFiles);

        // ── STEP 2: Detect secrets in config files ───────────────────────
        System.out.println("\n── STEP 2: Detecting secrets ──");
        List<ConfigEntry> allEntries = new ArrayList<>();

        for (String configFile : configFiles) {
            List<ConfigEntry> secrets = secretDetector.detect(workspace, configFile);
            allEntries.addAll(secrets);
        }

        System.out.println("   Found " + allEntries.size() + " secret entries");

        // ── STEP 3: Extract env variable references from source code ─────
        System.out.println("\n── STEP 3: Extracting env variable references ──");
        Set<String> referencedVars = envExtractor.extract(workspace);
        result.setReferencedEnvVars(new ArrayList<>(referencedVars));

        // ── STEP 4: Detect external dependencies ─────────────────────────
        System.out.println("\n── STEP 4: Detecting external dependencies ──");
        DependencyDetector.DependencyResult deps = dependencyDetector.detect(workspace);
        result.setDatabases(deps.databases());
        result.setCaches(deps.caches());

        // ── STEP 5: Normalize all config keys ────────────────────────────
        System.out.println("\n── STEP 5: Normalizing config keys ──");
        allEntries = normalizer.normalize(allEntries);
        result.setEntries(allEntries);

        // ── STEP 6: Sanitize secrets in source files ─────────────────────
        System.out.println("\n── STEP 6: Sanitizing secrets in source files ──");
        List<String> sanitizedFiles = sanitizer.sanitize(workspace, allEntries);
        result.setSanitizedFiles(sanitizedFiles);

        // ── STEP 7: Build environment variable map ───────────────────────
        System.out.println("\n── STEP 7: Building environment map ──");
        Map<String, String> envMap = new LinkedHashMap<>();

        // Add detected secrets as env vars
        for (ConfigEntry entry : allEntries) {
            if (entry.getNormalizedKey() != null && entry.getValue() != null) {
                envMap.put(entry.getNormalizedKey(), entry.getValue());
            }
        }

        // Add referenced env vars (with placeholder values if not already known)
        for (String refVar : referencedVars) {
            if (!envMap.containsKey(refVar)) {
                envMap.put(refVar, "");
            }
        }

        // ── STEP 8: Apply multi-injection strategy ───────────────────────
        System.out.println("\n── STEP 8: Applying multi-injection strategy ──");
        envMap = injectionEngine.applyMultiInjection(envMap, deps.databases(), deps.caches());
        result.setEnvMap(envMap);

        // ── STEP 9: Generate Docker -e flags ─────────────────────────────
        System.out.println("\n── STEP 9: Generating Docker env flags ──");
        List<String> dockerFlags = injectionEngine.buildDockerEnvFlags(envMap);
        result.setDockerEnvFlags(dockerFlags);

        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("  🧠 CONFIG INTELLIGENCE PIPELINE — COMPLETE");
        System.out.println("  Config files: " + configFiles.size());
        System.out.println("  Secrets:      " + allEntries.stream().filter(ConfigEntry::isSecret).count());
        System.out.println("  Env vars:     " + envMap.size());
        System.out.println("  Databases:    " + deps.databases());
        System.out.println("  Caches:       " + deps.caches());
        System.out.println("  Sanitized:    " + sanitizedFiles.size() + " files");
        System.out.println("═══════════════════════════════════════════════════════");

        return result;
    }
}
