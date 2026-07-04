# ADR 002: Repository Intelligence Engine (Updated)

## Status
Accepted

## Context
Deployrix requires a world-class, extensible discovery phase that avoids duplicated filesystem scanning. As the platform scales to 100,000+ repositories, the discovery mechanisms must be strictly modular, highly observable, and resilient. Direct instantiation of scanners and boolean capability flags do not provide enough context (provenance, confidence) for reliable architectural decisions and debugging.

## Decision
We will introduce the **Repository Intelligence Engine** with the following production-grade primitives:

1. **RepositoryModel Versioning & Caching**: The model must track `schemaVersion`, `generatedAt`, `engineVersion`, and `repositoryHash`. Unchanged hashes will skip discovery to dramatically speed up redeployments.
2. **Detector Registry**: Detectors are registered via a formal `DetectorRegistry` (e.g., `register(LanguageDetector)`). New languages or plugins can be added dynamically without modifying the core engine.
3. **Detector Independence & Output Merging**: Detectors never interact with each other. They emit isolated `DetectorResult` objects, which the engine merges into the `RepositoryModel`.
4. **Confidence Scoring & Provenance**: Every discovered property must include a confidence score (e.g., `0.97`) and strict provenance mapping (e.g., `Evidence: pom.xml Line 42`). This enforces strict observability and allows capability negotiation (vs binary failure).
5. **Incremental Discovery**: Future iterations will support Git-diff based rescanning to only rerun detectors affected by modified files.

## Consequences
- **Positive:** Debugging deployment decisions is trivial, as the `RepositoryModel` acts as a complete "Discovery Report" outlining exactly *why* a decision was made.
- **Positive:** Plugins (e.g., Rust Plugin) can be injected cleanly via the `DetectorRegistry`.
- **Negative:** The internal data models for capabilities must become richer (`CapabilityResult` instead of a simple `boolean`), slightly increasing object allocation during the initial scan phase.
- **Migration Strategy:** The new abstractions will be built in isolation. Legacy execution paths will remain active until the `ManifestBuilder` and `ExecutionGraph` modules are mature enough to consume this sophisticated intelligence model.
