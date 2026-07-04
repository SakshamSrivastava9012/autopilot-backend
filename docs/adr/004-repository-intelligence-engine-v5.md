# ADR-004: Repository Intelligence Engine V5 — Centralized Immutable Discovery

## Status

Accepted

## Date

2026-07-03

## Context

Deployrix has evolved through incremental patches. Each new deployment fix (asset rewriting, OAuth tolerance, database provisioning, health negotiation) added filesystem scanning logic in its own subsystem. This created several architectural problems:

1. **Multiple rescans** — The repository was scanned independently by the analyzer, the environment resolver, the deployment planner, the asset patcher, and the runtime inspector. Each scan could produce different results depending on timing and state.
2. **Mutable metadata** — Discovery results were passed as mutable `HashMap<String, Object>` between pipeline stages, leading to silent overwrites and conflicting values.
3. **Framework coupling** — Deployment decisions were driven by framework names (`Spring Boot`, `Next.js`) instead of runtime capabilities (`REST_API`, `SSR`, `SPA`).
4. **Non-determinism** — Two deployments of the same commit could produce different metadata due to race conditions in scanning.

## Decision

We introduce the **Repository Intelligence Engine V5**, a single deterministic phase that:

1. Executes **exactly once** per deployment lifecycle.
2. Produces an **immutable `RepositoryModelV5`** (using `@Value` instead of `@Data`).
3. Serves as the **single source of truth** consumed by all downstream subsystems.
4. **Never provisions, builds, deploys, or injects** — it only observes.

### Architecture

```
Repository (filesystem)
       │
       ▼
┌──────────────────────────────────┐
│  RepositoryIntelligenceEngineV5  │
│  ┌────────────────────────────┐  │
│  │    DetectorRegistryV5      │  │
│  │  ┌──────────────────────┐  │  │
│  │  │ LanguageDetectorV5   │  │  │
│  │  │ FrameworkDetectorV5  │  │  │
│  │  │ CapabilityDetectorV5 │  │  │
│  │  │ ServiceDetectorV5    │  │  │
│  │  │ DependencyDetectorV5 │  │  │
│  │  │ AssetDetectorV5      │  │  │
│  │  │ SecretDetectorV5     │  │  │
│  │  └──────────────────────┘  │  │
│  └────────────────────────────┘  │
└──────────────┬───────────────────┘
               │ (immutable)
               ▼
      RepositoryModelV5
               │
               ▼
       ManifestBuilder
               │ (immutable)
               ▼
      DeploymentManifest
               │
    ┌──────────┼──────────┐
    ▼          ▼          ▼
 Runtime   Dependency  Verification
 Engine    Negotiation  Platform
```

### Key Principles

- **Immutability** — `@Value` (Lombok) produces truly immutable objects with no setters.
- **Evidence-backed** — Every `DetectorResultV5` requires confidence scores and provenance.
- **Capability-driven** — Downstream systems depend on capabilities (`REST_API`, `SSR`) not framework names.
- **Zero side effects** — The engine reads the filesystem and returns a value. Nothing else.
- **Pluggable** — New detectors are registered simply by implementing `RepositoryDetector` and adding `@Component`.

## Consequences

### Positive

- Repository is scanned exactly once — eliminates scan drift between stages.
- All downstream subsystems share a single, consistent view of the repository.
- New languages and frameworks require only a new detector implementation.
- Discovery performance is measurable via `DiscoveryTimeline`.
- Legacy pipeline remains fully operational (backward compatible).

### Negative

- The V5 model coexists temporarily with the V4 `RepositoryModel` until migration completes.
- Detectors must remain conservative in confidence scoring to avoid false capabilities.

### Migration Path

The legacy `RepositoryIntelligenceEngine` (V4) and `RepoAnalyzerService` remain untouched. The V5 engine runs alongside them. Future prompts will incrementally migrate each downstream consumer to use `RepositoryModelV5` instead of re-scanning the filesystem.
