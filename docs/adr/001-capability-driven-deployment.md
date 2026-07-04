# ADR 001: Capability-Driven Modular Deployment Architecture

## Status
Accepted

## Context
The current Deployrix (Autopilot) deployment engine relies on an imperative, monolithic `DeploymentPipelineService`. Fixes and optimizations often mutate containers and inject configurations globally based on framework-specific `if (framework == NEXTJS)` checks. This introduces architectural coupling, leading to regressions where fixing static assets for one framework breaks API routing for another. 

To support 100,000+ arbitrary repositories, the deployment pipeline must scale without requiring internal modifications for every new framework or language paradigm.

## Decision
We are introducing a **Capability-Driven Engine**, analogous to Kubernetes Operators or Terraform Providers.

1. **Immutable Source of Truth:** 
   The `DeploymentManifest` becomes immutable during execution. Components will no longer recompute metadata (ports, base paths, route structures).
2. **Capabilities over Frameworks:** 
   The system will identify abstract capabilities (`SPA`, `SSR`, `REST_API`, `DATABASE_REQUIRED`) rather than framework names. 
3. **Module Interception:** 
   We introduce `CompatibilityModule`, replacing hardcoded steps. Each module operates solely if it `supports(manifest)` based on exposed capabilities.
4. **Separation of Plan & Apply:** 
   Modules will emit a `List<Operation>` during a side-effect-free `plan()` phase. The engine then passes these operations to an `apply()` phase, enabling dry-runs and easier testing.
5. **Runtime Snapshotting:**
   Rather than performing inline verifications, the system creates a `RuntimeSnapshot` (containing active ports, asset graphs, proxy outputs) and uses specialized validators to execute layered verification.

## Consequences
- **Positive:** Adding a new language (e.g., Python FastAPI) requires no changes to core orchestration. A module supporting `REST_API` naturally absorbs it.
- **Positive:** Testing becomes trivial since `plan()` returns assertions of intended actions (e.g., `CreateRouteOperation`) without spinning up Docker or Nginx.
- **Negative:** Existing deployment orchestration logic must be gradually phased out into separate modules. This requires a strict, multi-milestone migration strategy to ensure zero breakage of backward compatibility.
