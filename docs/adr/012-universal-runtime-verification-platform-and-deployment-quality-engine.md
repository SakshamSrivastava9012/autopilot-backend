# ADR-012: Universal Runtime Verification Platform & Deployment Quality Engine

## Status

Accepted

## Date

2026-07-03

## Context

Prior to V5 Milestone 4B.2, deployment verification in Deployrix was monolithic, brittle, and overly sensitive:

1. **Binary Pass/Fail Failure** — Non-critical findings (such as missing favicons or minor performance warnings) immediately failed production deployments.
2. **False Positive OAuth Failures** — Secured endpoints returning HTTP `302/303` redirects or `401/403` auth challenges were misclassified as deployment failures.
3. **Repository Rescanning** — Legacy validation rescanned source files and filesystem assets instead of evaluating live runtime state.
4. **Hardcoded Severity Thresholds** — Verification severity was hardcoded regardless of whether the target environment was Development, Staging, or Production.

## Decision

We introduce the **Universal Runtime Verification Platform V5** (`RuntimeVerificationPlatformV5`), **Deployment Quality Engine** (`DeploymentQualityEngine`), **Verification Policy Engine** (`VerificationPolicyEngine`), and 9 capability-specific verification modules with the following mandatory architectural rules:

> 1. **Verification begins only after the Runtime Lifecycle Engine reaches the STABLE state.**
> 2. **Verification modules are independent and capability-driven.**
> 3. **Only CRITICAL verification failures may fail a deployment.**
> 4. **OAuth redirects (302/303) and authentication responses (401/403) are evaluated according to the negotiated startup strategy, not treated as generic failures.**
> 5. **Runtime verification observes deployed applications only; it never modifies, redeploys, or reprovisions infrastructure.**
> 6. **Deployment quality is represented by a structured DeploymentQualityReport with severity levels and an overall quality score rather than a simple pass/fail result.**
> 7. **Verification modules must consume immutable runtime contracts and snapshots, never rescan repositories or infer framework behavior independently.**
> 8. **Verification Policy Engine evaluates findings dynamically according to active environment policies (Development vs Production).**

### Capability Verification Modules

The verification platform consists of 9 decoupled capability modules:

- **`BrowserVerificationModule`**: Evaluates DOM hydration, JavaScript exceptions, and SPA client-side routing.
- **`AssetVerificationModule`**: Verifies CSS, JS, fonts, images, MIME types, and HTTP cache headers at runtime.
- **`RouteVerificationModule`**: Evaluates negotiated routes from `RuntimeManifest` (never invents unannounced endpoints).
- **`APIVerificationModule`**: Evaluates REST/GraphQL endpoints, expected status code ranges, CORS headers, and latency SLAs.
- **`OAuthVerificationModule`**: Evaluates authentication flows — classifies HTTP `302, 303, 307, 308, 401, 403` as EXPECTED HEALTHY.
- **`PerformanceVerificationModule`**: Collects TTFB, LCP, CLS, FCP, and bundle metrics. Generates warnings only; **NEVER fails a deployment**.
- **`SecurityVerificationModule`**: Checks HTTPS enforcement, HSTS, CSP, cookie security flags, and mixed content.
- **`DependencyVerificationModule`**: Confirms active runtime dependencies (databases, Redis, Kafka, RabbitMQ) are reachable without reprovisioning.
- **`ContainerVerificationModule`**: Assesses container status, restart count, exit codes, and OOM events.

### Architecture

```
ExecutionGraph Node [verification-node]
                 │
                 ▼
  RuntimeVerificationPlatformV5 (Executes after STABLE state)
                 │
                 ├─► VerificationPolicyEngine (Development vs Production Policy)
                 │
                 ├─► 9 Capability-Driven Verification Modules (Independent)
                 │
                 ▼
  DeploymentQualityEngine (Computes Quality Score 100 ➔ 0)
                 │
                 ▼
  RuntimeContext (VerificationSnapshot & DeploymentQualityReport)
```

## Consequences

### Positive

- Completely eliminates false deployment failures caused by OAuth redirects or missing non-critical static assets.
- Provides granular quality scores (100 down to 0) rather than blunt pass/fail decisions.
- Decouples environment policy (Dev vs Prod severity thresholds) from core verification logic via `VerificationPolicyEngine`.
- Guaranteed zero runtime side-effects: verification only observes and never mutates containers or infrastructure.

### Negative

- Requires maintenance of independent verification modules as new web capabilities emerge.

### Migration Path

Activated in `V5_PRODUCTION` engine profile via `VerificationModuleV5` adapter connected to `ExecutionGraph`.
