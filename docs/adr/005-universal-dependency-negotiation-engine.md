# ADR-005: Universal Dependency Negotiation Engine

## Status

Accepted

## Date

2026-07-03

## Context

Deployrix's dependency provisioning suffered from a fundamental architectural flaw: the same code that **decided** which database provider to use also **executed** the provisioning (calling Docker, Terraform, AWS APIs). This coupling caused three classes of regressions:

1. **Premature validation** — The engine attempted DNS resolution and TCP connections during the decision phase, failing deployments before containers were even created.
2. **Environment mutation** — The engine injected conflicting environment variables (e.g., both `SPRING_DATASOURCE_URL` and `DATABASE_URL`) because it couldn't distinguish which style the application consumed.
3. **Provider lock-in** — Decisions were made based on framework names instead of observed connection patterns, causing incorrect RDS vs Docker selections.

## Decision

We introduce the **Universal Dependency Negotiation Engine** (V5 Milestone 2) with one inviolable rule:

> **Negotiation decides intent; provisioning executes intent. Negotiation must never perform network I/O, DNS resolution, authentication, Docker operations, or cloud provisioning.**

### Architecture

```
RepositoryModelV5 (immutable, from Milestone 1)
         │
         ▼
┌─────────────────────────────────────┐
│  DependencyIntelligenceEngine       │  Classifies endpoints (dev vs prod)
│  ConfigurationClassifier            │  Pure string pattern matching
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  DependencyNegotiationEngineV5      │  Decides provider preference
│  ConfigurationNegotiationEngineV5   │  Decides config style
│  MigrationDiscoveryEngine           │  Detects migration tooling
└──────────────┬──────────────────────┘
               │
               ▼
     Immutable Contracts
  ┌──────────────────────────┐
  │ DependencyContract       │
  │ ConfigurationContractV5  │
  │ ServiceContract          │
  │ MigrationContract        │
  │ CredentialContract        │
  │ NegotiationReport        │
  └──────────────────────────┘
               │
               ▼
       DeploymentManifest
               │
     ┌─────────┴──────────┐
     ▼                    ▼
  Provisioning       Environment
  Layer (V5.3+)      Injector (V5.3+)
```

### Decision Tree (Priority Order)

| Priority | Rule | Action |
|----------|------|--------|
| 1 | Explicit user preference from UI | Never violated |
| 2 | Repository has production endpoint (e.g., `mongodb+srv://`, `*.rds.amazonaws.com`) | EXISTING_EXTERNAL |
| 3 | Repository has development endpoint (e.g., `localhost`, `127.0.0.1`) | PLATFORM_MANAGED or DOCKER_RUNTIME |
| 4 | No endpoint detected | Platform defaults |

### Configuration Style Selection

Each application receives **exactly one** configuration model:

| Language/Framework | Style | Example Variable |
|-------------------|-------|-----------------|
| Spring Boot | `SPRING_DATASOURCE` | `SPRING_DATASOURCE_URL` |
| Node.js / Express | `DATABASE_URL` | `DATABASE_URL` |
| Django / FastAPI | `DATABASE_URL` | `DATABASE_URL` |
| Laravel | `DB_HOST` | `DB_HOST`, `DB_USER`, `DB_NAME` |
| Next.js / Nuxt | `NEXT_PUBLIC_ENV` | `NEXT_PUBLIC_DATABASE_URL` |

**Never mix styles.** An application must never receive both `SPRING_DATASOURCE_URL` and `DATABASE_URL`.

### Endpoint Classification

The `ConfigurationClassifier` uses pure string pattern matching (no network I/O) to classify endpoints:

- `localhost`, `127.0.0.1` → **LOCALHOST** (development)
- `*.rds.amazonaws.com`, `*.neon.tech` → **CLOUD_DATABASE** (production)
- `10.*`, `192.168.*` → **PRIVATE_NETWORK**
- Service names without dots → **DOCKER_SERVICE**

## Consequences

### Positive

- Negotiation and provisioning are fully decoupled — provisioning bugs cannot corrupt negotiation.
- No conflicting environment variables — exactly one config style per service.
- Development endpoints are automatically recognized and replaced with production alternatives.
- User preferences from the Deployrix UI are always honored (Priority 1).
- All decisions produce auditable `NegotiationReport` entries for the dashboard.

### Negative

- The engine cannot verify if an external endpoint is actually reachable (by design).
- Migration from the V4 `DependencyProvisionService` requires incremental adoption.

### Migration Path

The legacy `DependencyProvisionService` (V4) remains active. V5.2 contracts are produced alongside V4 outputs. Future V5.3 provisioning layer will consume these contracts to execute the intent.
