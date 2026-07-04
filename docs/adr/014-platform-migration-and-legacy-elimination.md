# ADR-014: Platform Migration & Legacy Elimination

## Status

Accepted

## Date

2026-07-03

## Context

Following V5 Milestones 1–4B.3, Deployrix possesses a complete graph-driven, capability-based runtime engine (`DeploymentRuntimeEngineV5`). However, legacy procedural services (`DeploymentPipelineService`, `HealthCheckService`, `UniversalNginxGenerator`, etc.) existed alongside the V5 architecture.

Dual-engine architectures introduce risk:
1. **Execution Divergence** — Discrepancies between legacy imperative loops and V5 contract-driven graphs.
2. **Duplicated Logic** — Multiple services generating Nginx configs, inspecting assets, or performing health checks.
3. **High Maintenance Overhead** — Maintaining dual feature paths across versions.

## Decision

We transition Deployrix to a **single-engine production architecture** led by `DeploymentRuntimeEngineV5` and introduce `DeploymentSession` as the root deployment object, with the following mandatory architectural rules:

> 1. **There must be exactly one production execution engine (`DeploymentRuntimeEngineV5`).**
> 2. **Legacy components may exist only as adapters during migration.**
> 3. **No new functionality may be added to legacy services.**
> 4. **Every legacy component must have a documented replacement and removal plan (`ReplacementMatrix`).**
> 5. **Every deployment is represented by exactly one immutable `DeploymentSession`.**
> 6. **Feature parity must be demonstrated through automated regression tests before removing any legacy implementation.**

### Single Source of Truth: `DeploymentSession`

Every deployment execution produces a single, immutable `DeploymentSession` containing:
- `RepositoryModelV5`
- `DeploymentManifest`
- `BuildArtifact`
- `InfrastructureSnapshot`
- `DependencySnapshot`
- `RuntimeEnvironmentSnapshot`
- `RuntimeLifecycleSnapshot`
- `ReverseProxySnapshot`
- `VerificationSnapshot`
- `DeploymentQualityReport`

### Legacy Replacement Matrix

| Legacy Component | V5 Single Engine Replacement | Status |
|------------------|------------------------------|--------|
| `DeploymentPipelineService` | `DeploymentRuntimeEngineV5` | Migrated via Adapter |
| `HealthCheckService` | `HealthNegotiationEngineV5` | Migrated via Adapter |
| `UniversalNginxGenerator` | `ReverseProxyEngineV5` | Migrated via Adapter |
| `AssetPatcherService` | `AssetRouter` | Migrated via Adapter |
| `EnvironmentResolver` | `EnvironmentInjectionEngineV5` | Migrated via Adapter |
| `DeploymentValidationSuite` | `RuntimeVerificationPlatformV5` | Migrated via Adapter |
| `DependencyProvisionService` | `DependencyProvisionEngineV5` | Migrated via Adapter |

## Consequences

### Positive

- Guarantees zero duplicate execution logic: exactly one engine, one execution graph, one runtime context.
- Guarantees backward compatibility for existing caller APIs through delegating compatibility adapters.
- Prepares the codebase for clean legacy deletion in V6.0 without breaking existing integrations.

### Negative

- Temporary presence of compatibility adapters until callers transition completely to V5 APIs.

### Migration Path

Activated in `V5_PRODUCTION` engine profile via `PlatformMigrationEngine` and `DeploymentRuntimeEngineV5`.
