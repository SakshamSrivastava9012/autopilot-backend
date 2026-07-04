# ADR-006: Universal Build & Runtime Inspection Engine

## Status

Accepted

## Date

2026-07-03

## Context

In earlier iterations of Deployrix, runtime inspection and asset handling were intertwined with artifact modification. Inspections would attempt to patch asset files, rewrite HTML `<base>` tags, or rebuild Docker images post-inspection to inject runtime proxies or scripts. This created several critical failure modes:

1. **Non-deterministic Builds** — Modifying container artifacts after the initial build created discrepancies between local test builds and production deployments.
2. **Broken Asset Hashes** — Rewriting built JavaScript or CSS bundles broke asset integrity hashes and cache invalidation.
3. **Flaky Inspections** — Running inspect logic in mutating containers introduced side effects that modified application state or filesystem structures before launch.

## Decision

We establish the **Universal Build Engine & Runtime Inspection Platform** (V5 Milestone 3) with the following mandatory architectural rule:

> **Runtime inspection is observational, not transformational. Images are immutable after build. Runtime adaptation is expressed through metadata contracts consumed by later deployment stages, never by modifying application artifacts.**

### Key Architectural Principles

1. **Build Immutability** — The `BuildEngineV5` builds a container image exactly once, producing an immutable `BuildArtifact`. Downstream stages may never rebuild or patch this image.
2. **Isolated Inspection** — `RuntimeInspectionEngineV5` executes inspection in an isolated, temporary container. It observes entrypoints, exposed ports, health strategies, and filesystem layouts without ever attaching to a public network or persisting container mutations. The temporary container is immediately destroyed.
3. **Metadata-Driven Adaptation** — Instead of rewriting assets or injecting scripts into application files, runtime adaptations (such as SPA history fallbacks, static asset aliases, OAuth callback routing, and base paths) are generated as metadata contracts (`CompatibilityContract`, `RuntimeResolverMetadata`, `AssetManifestV5`). These contracts are passed to reverse proxies and deployment orchestrators in later stages.

### Architecture

```
RepositoryModelV5 (immutable)
       │
       ▼
BuildStrategyResolver ──► BuildPlan
       │
       ▼
 BuildEngineV5 ───────► BuildArtifact (immutable image)
       │
       ▼
RuntimeInspectionEngineV5 (isolated, read-only temporary container)
       │
       ├─► RuntimeManifestV5
       ├─► AssetIntelligenceEngine ─────► AssetManifestV5
       ├─► RuntimeCompatibilityAnalyzer ──► CompatibilityContract & RuntimeResolverMetadata
       └─► InspectionReports & Timeline
```

## Consequences

### Positive

- Container artifacts are 100% deterministic and reproducible.
- Asset bundle integrity and browser caching are preserved without post-build file rewrites.
- Inspection executes with zero side effects on production images.
- Later deployment stages (e.g. reverse proxy routing, environment injection) rely on explicit metadata contracts rather than arbitrary code patches.

### Negative

- Reverse proxy and deployment orchestration layers must consume `RuntimeResolverMetadata` to handle path prefixes and history fallbacks externally.

### Migration Path

Legacy build routines and `RuntimeInspectionService` continue to operate for backward compatibility while V5 deployment pipelines adopt `BuildEngineV5` and `RuntimeInspectionEngineV5`.
