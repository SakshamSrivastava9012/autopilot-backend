package com.autopilot.service.deployment.intelligence.v5.model;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single, immutable source of truth about a repository.
 * Produced exactly once by the RepositoryIntelligenceEngineV5.
 * No downstream component may rescan the filesystem after this model exists.
 *
 * This is the Deployrix equivalent of Kubernetes API resource discovery.
 * Its responsibility is observation — never execution.
 *
 * @since V5 — ADR-004
 */
@Value
@Builder
public class RepositoryModelV5 {

    // ─── Versioning & Provenance ───────────────────────────
    String schemaVersion;
    String engineVersion;
    long generatedAt;
    String repositoryHash;
    String commitHash;
    String branch;
    String repositoryUrl;

    // ─── Workspace ─────────────────────────────────────────
    String workspace; // Absolute path to the root of the cloned repository

    // ─── Language & Build ──────────────────────────────────
    Set<String> languages;
    Set<String> frameworks; // Informational only — never drives deployment logic
    Set<String> packageManagers;
    Set<String> buildSystems;

    // ─── Services ──────────────────────────────────────────
    List<ServiceDescriptorV5> services;

    // ─── Capabilities (drive deployment decisions) ─────────
    Set<String> capabilities;

    // ─── Dependencies (detected, never provisioned here) ──
    List<DependencyDefinition> dependencies;

    // ─── Configuration ─────────────────────────────────────
    List<EnvironmentDefinition> environmentDefinitions;

    // ─── Assets ────────────────────────────────────────────
    List<AssetDefinition> assets;

    // ─── Routes ────────────────────────────────────────────
    List<RouteDefinition> routes;

    // ─── Secrets ───────────────────────────────────────────
    List<SecretDefinition> secrets;

    // ─── Metadata & Warnings ───────────────────────────────
    Map<String, String> metadata;
    List<String> warnings;

    // ─── Discovery Timeline ────────────────────────────────
    DiscoveryTimeline discoveryTimeline;
}
