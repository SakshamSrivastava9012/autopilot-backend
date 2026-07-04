# ADR-010: Universal Runtime Connection Resolution & Environment Injection Engine

## Status

Accepted

## Date

2026-07-03

## Context

Prior to V5 Milestone 4A.4, environment variable injection in Deployrix suffered from conflated responsibilities, conflicting environment variables, and repository file mutation:

1. **Conflicting Configuration Injection** — Deployments simultaneously injected multiple database variables (`SPRING_DATASOURCE_URL`, `DATABASE_URL`, `DB_HOST`, `DB_USER`) into containers, causing framework startup failures and connection reset errors.
2. **Repository File Mutation** — Legacy code attempted to rewrite `.env` files, `application.yml`, or `package.json` in the source repository during deployment.
3. **Leaked Development Defaults** — Development connection strings (`localhost:5432`, `root`, `password`, `admin`) from repository defaults frequently bypassed filtering and reached production containers.
4. **Hardcoded Secret Managers** — Applications were tightly coupled to specific secret resolution mechanisms rather than a pluggable abstraction.

## Decision

We introduce the **Universal Runtime Connection Resolution & Environment Injection Engine V5** (`EnvironmentInjectionEngineV5`), **Runtime Connection Resolver** (`RuntimeConnectionResolver`), **Framework Configuration Mapper** (`FrameworkConfigurationMapper`), **Configuration Sanitizer** (`ConfigurationSanitizer`), and **Secret Reference Resolver** (`SecretReferenceResolver`) with the following mandatory architectural rules:

> 1. **`RuntimeConnectionContract` is the only source of connection information.**
> 2. **Applications never consume provider-specific contracts directly.**
> 3. **Framework mappings are deterministic and one-to-one.**
> 4. **Generated runtime environments never modify repository files.**
> 5. **Environment generation is the only stage allowed to inject application configuration.**
> 6. **Secret resolution is provider-agnostic and pluggable.**
> 7. **Future providers (Azure Key Vault, GCP Secret Manager, HashiCorp Vault, Kubernetes Secrets) must integrate through the `SecretReferenceResolver` interface without requiring changes to the Environment Injection Engine.**

### Key Architectural Principles

1. **Single Connection Source** — `RuntimeConnectionContract` acts as the single source of truth for runtime connection parameters, decoupling framework configuration from infrastructure providers.
2. **Deterministic Framework Mappings** — `FrameworkConfigurationMapper` translates connection contracts into exactly ONE framework mapping schema (Spring Boot: `SPRING_DATASOURCE_*`, Node/Prisma: `DATABASE_URL`, Laravel: `DB_*`, Rails: `DATABASE_URL`). Mixed configuration styles are strictly prohibited.
3. **Configuration Sanitization** — `ConfigurationSanitizer` automatically filters conflicting variables and removes repository development defaults (`localhost`, `127.0.0.1`, `root`, `password`, `admin`).
4. **Pluggable Provider-Agnostic Secret Resolution** — `SecretReferenceResolver` resolves secret references into container environment variables without exposing cloud provider SDKs to application code.
5. **Zero File Mutation** — Generated environments exist exclusively in-memory as immutable `ContainerEnvironment` contracts passed directly to container execution runtime nodes. Source files, Dockerfiles, and `.env` files remain untouched.

### Architecture

```
ExecutionGraph Node [credential-node]
                 │
                 ▼
  RuntimeConnectionResolver ──► RuntimeConnectionContract (Immutable single source of truth)
                 │
                 ▼
  FrameworkConfigurationMapper (1-to-1 deterministic mapping for Spring, Node, Laravel, Django, Rails)
                 │
                 ▼
  ConfigurationSanitizer (Filters conflicts & removes localhost/root dev defaults)
                 │
                 ▼
  SecretReferenceResolver (Provider-agnostic secret resolution: AWS Secrets, Vault, Docker Secrets)
                 │
                 ▼
  EnvironmentInjectionEngineV5 ──► ContainerEnvironment & RuntimeEnvironmentSnapshot
```

## Consequences

### Positive

- Completely eliminates conflicting environment variable bugs (`SPRING_DATASOURCE_URL` vs `DATABASE_URL`).
- Guarantees zero repository file modification or source code mutation.
- Hardened security: dev defaults and raw provider secrets never reach container runtime.
- Extensible: adding support for Azure Key Vault, GCP Secret Manager, or HashiCorp Vault requires only implementing a `SecretReferenceResolver` plugin.

### Negative

- Every target framework must be registered in `FrameworkConfigurationMapper`.

### Migration Path

Legacy environment injection remains available behind feature flag `deployrix.runtime.environment=v5`. When enabled, `CredentialModuleV5` delegates execution to `EnvironmentInjectionEngineV5`.
