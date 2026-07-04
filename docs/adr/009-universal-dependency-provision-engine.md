# ADR-009: Universal Dependency Provision Engine & Credential Resolution Platform

## Status

Accepted

## Date

2026-07-03

## Context

Prior to V5 Milestone 4A.3, dependency provisioning in Deployrix suffered from coupling and fragile assumptions:

1. **Hardcoded Fallback Credentials** — Default strings (e.g. `root`, `admin`, `postgres`) were frequently injected, creating security hazards and authentication failures.
2. **Fixed Sleeps & Imperative Polling** — The deployment engine used `Thread.sleep()` to wait for database readiness, leading to flaky health checks or unnecessary delays.
3. **Single Dependency Instance Limitation** — Deployments assumed at most one database or cache per stack, preventing multi-database models (e.g. `primary-db` + `analytics-db` or `cache-redis` + `session-redis`).
4. **Mixed Layer Responsibilities** — Infrastructure creation, dependency instantiation, and credential generation were combined into monolithic steps.

## Decision

We introduce the **Universal Dependency Provision Engine V5** (`DependencyProvisionEngineV5`), **Credential Resolver** (`CredentialResolver`), **Dependency Health Waiter** (`DependencyHealthWaiter`), and **Dependency Provider Adapter Registry** (`DependencyProviderRegistry`) with the following mandatory architectural rules:

> 1. **Dependency provisioning executes immutable contracts only.**
> 2. **Credentials are resolved from the provider, never invented.**
> 3. **Infrastructure, dependency provisioning, and application deployment are independent layers.**
> 4. **External resources are never modified or destroyed.**
> 5. **Runtime readiness must be event-driven and health-based, never based on fixed sleeps.**
> 6. **Support multiple instances of the same dependency type keyed by unique `dependencyId` (`primary-db`, `analytics-db`, `session-cache`).**

### Key Architectural Principles

1. **Unique Dependency Keying** — Dependencies are keyed by unique `dependencyId` (e.g., `primary-db`, `analytics-db`, `session-cache`), enabling full support for poly-database architectures.
2. **Provider Credential Resolution** — `CredentialResolver` retrieves credentials strictly from user configuration (external resources), cloud secrets managers (`aws`), or container runtime environments (`docker`). Invented fallback credentials are strictly prohibited.
3. **Event-Driven Non-Blocking Health Waiting** — `DependencyHealthWaiter` evaluates container health checks, cloud provider status APIs, or TCP/ping probes using non-blocking, adaptive timeouts. `Thread.sleep()` is forbidden.
4. **Decoupled Provider Adapters** — `DependencyProviderRegistry` auto-discovers provider implementations (`DockerDependencyAdapter`, `AWSManagedDependencyAdapter`, `ExternalDependencyAdapter`) via Spring DI with zero switch statements.
5. **Safe Rollback** — `ExternalDependencyAdapter` preserves user-owned resources during rollback (`RETAIN` / `PRESERVE`), while platform-managed adapters delete only platform-created containers/instances.

### Architecture

```
ExecutionGraph Node [dependency-node]
                 │
                 ▼
  DependencyProvisionEngineV5
                 │
                 ├─► CredentialResolver ──► ResolvedCredentialContract (Never invented credentials)
                 │
                 ├─► DependencyProviderRegistry
                 │      ├─► DockerDependencyAdapter
                 │      ├─► AWSManagedDependencyAdapter
                 │      └─► ExternalDependencyAdapter
                 │
                 └─► DependencyHealthWaiter (Non-blocking, event-driven readiness check)
                 │
                 ▼
  RuntimeContext (RuntimeDependencies & ResolvedCredentialContracts keyed by dependencyId)
```

## Consequences

### Positive

- Enables multi-database and multi-cache production deployments (`primary-db`, `analytics-db`, `session-cache`).
- Eliminates hardcoded credential security risks and false authentication failures.
- Non-blocking, event-driven health checks reduce deployment wait times while preventing timeout flakes.
- External databases are strictly protected from rollback destruction.

### Negative

- Every runtime dependency must declare a unique `dependencyId`.

### Migration Path

Legacy dependency code continues to run behind feature flag `deployrix.runtime.dependency=v5`. When active, `DependencyModuleV5` delegates execution to `DependencyProvisionEngineV5`.
