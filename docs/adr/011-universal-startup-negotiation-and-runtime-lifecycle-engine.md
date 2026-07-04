# ADR-011: Universal Startup Negotiation & Runtime Lifecycle Engine

## Status

Accepted

## Date

2026-07-03

## Context

Prior to V5 Milestone 4B.1, application container startup in Deployrix relied on fragile assumptions and fixed timing:

1. **Fixed Sleep Timing** — Deployments used arbitrary delay loops (`Thread.sleep()`), causing unnecessary deployment latency or premature startup failure for slower-starting frameworks.
2. **Conflated Readiness & Liveness** — Container readiness (ability to serve traffic) and liveness (process vitality) were treated as a single binary check.
3. **Misclassified HTTP Statuses** — OAuth redirects (`302`/`303`) and authentication endpoints (`401`/`403`) were misclassified as failures, marking healthy secured applications as `FAILED`.
4. **Fragmented Feature Flags** — Separate feature flags (`deployrix.runtime.engine`, `deployrix.runtime.infrastructure`, etc.) introduced configuration complexity and risk of mixed-mode execution.

## Decision

We introduce the **Universal Startup Negotiation & Runtime Lifecycle Engine V5** (`RuntimeLifecycleEngineV5`), **Startup Negotiation Engine** (`StartupNegotiationEngineV5`), **Readiness Engine** (`ReadinessNegotiationEngine`), **Health Engine** (`HealthNegotiationEngineV5`), and **Engine Profile Manager** (`EngineProfileManager`) with the following mandatory architectural rules:

> 1. **Startup is driven by observed runtime state, never by fixed delays.**
> 2. **Readiness and liveness are separate concepts and must be negotiated independently.**
> 3. **OAuth redirects (302/303) and authentication responses (401/403) may represent healthy applications when consistent with the negotiated startup strategy.**
> 4. **Startup must adapt to runtime progress (logs, health checks, process state) rather than relying on fixed polling intervals.**
> 5. **Runtime lifecycle events are immutable and are the only source of startup state transitions.**
> 6. **The Runtime Lifecycle Engine orchestrates startup only; browser verification, asset verification, reverse proxy generation, and deployment quality assessment belong to later milestones.**
> 7. **Consolidate individual subsystem feature flags into a unified `EngineProfile` (`LEGACY`, `V5_EXPERIMENTAL`, `V5_STAGING`, `V5_PRODUCTION`).**

### Key Architectural Principles

1. **Strict 11-Step Lifecycle State Machine** — Applications transition deterministically through: `IMAGE_READY` ➔ `CONTAINER_CREATED` ➔ `CONTAINER_RUNNING` ➔ `PROCESS_RUNNING` ➔ `PORT_DISCOVERY` ➔ `READINESS_NEGOTIATION` ➔ `READINESS_CONFIRMED` ➔ `HEALTH_NEGOTIATION` ➔ `HEALTH_CONFIRMED` ➔ `READY` ➔ `STABLE`. No step may be skipped.
2. **Independent Readiness vs Liveness** — Readiness checks confirm port binding and endpoint availability for traffic routing. Liveness probes evaluate process health using framework-tailored strategies (HTTP, HTTPS, TCP, Docker HEALTHCHECK, Process Alive, OAuth Redirect, WebSocket, GraphQL, SSE).
3. **Comprehensive Healthy Status Classification** — HTTP `200, 201, 202, 204, 301, 302, 303, 307, 308, 401, 403` are classified as **EXPECTED HEALTHY**. Only `5xx`, process crashes, OOM kills, non-zero exits, or hard timeouts are classified as unhealthy.
4. **Adaptive Timeouts & Event-Driven Monitoring** — Non-blocking adaptive checking replaces `Thread.sleep()`. Timeouts extend dynamically when startup log progress or port binding progress is detected.
5. **Unified Engine Profile** — `EngineProfileManager` simplifies V5 configuration. Switching `deployrix.engine.profile` between `LEGACY`, `V5_EXPERIMENTAL`, `V5_STAGING`, and `V5_PRODUCTION` controls all V5 engines cleanly across the platform.

### Architecture

```
ExecutionGraph Node [startup-node]
                 │
                 ▼
  RuntimeLifecycleEngineV5 (EngineProfileManager Gated)
                 │
                 ├─► StartupNegotiationEngineV5 (Negotiates StartupContract)
                 │
                 ├─► ReadinessNegotiationEngine (Non-blocking readiness check)
                 │
                 └─► HealthNegotiationEngineV5 (Liveness probe & status classification)
                 │
                 ▼
  RuntimeLifecycleState Machine (IMAGE_READY ➔ ... ➔ STABLE)
                 │
                 ▼
  RuntimeContext (RuntimeLifecycleSnapshot & StartupReport)
```

## Consequences

### Positive

- Completely eliminates deployment flakiness caused by `Thread.sleep()` or fixed polling intervals.
- Correctly validates OAuth/Auth-protected services without misclassifying 302/401 responses as deployment failures.
- Unified `EngineProfile` dramatically simplifies configuration and prevents mixed-mode runtime states.
- Microsecond-precision runtime events improve dashboard diagnostics.

### Negative

- Every application must be monitored through the 11-step lifecycle state machine.

### Migration Path

System modes are managed centrally by setting `deployrix.engine.profile` (`LEGACY`, `V5_EXPERIMENTAL`, `V5_STAGING`, `V5_PRODUCTION`). When set to `V5_PRODUCTION`, `StartupModuleV5` delegates execution to `RuntimeLifecycleEngineV5`.
