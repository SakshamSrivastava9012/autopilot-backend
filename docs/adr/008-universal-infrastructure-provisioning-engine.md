# ADR-008: Universal Infrastructure Provisioning Engine

## Status

Accepted

## Date

2026-07-03

## Context

Prior to V5 Milestone 4A.2, infrastructure provisioning in Deployrix was mixed with repository inspection, provider negotiation, and application health checks. Individual provisioning methods directly called AWS SDKs or Docker CLI commands while attempting to infer technology stacks (e.g. checking whether a project uses MySQL vs Postgres). This created severe drawbacks:

1. **Mixed Concerns** — Infrastructure code was performing repository discovery and negotiating database types during provisioning execution.
2. **Coupling to Specific Cloud APIs** — Imperative `if (provider == AWS)` logic hindered adding new cloud providers (GCP, Azure, Kubernetes, Nomad).
3. **Orphaned Cloud Resources** — Failed deployments left orphaned AWS security groups, EC2 instances, or Docker bridge networks because resource creation lacked persistent state tracking.
4. **Unsafe Rollbacks** — Rollback logic did not distinguish between platform-created resources and user-owned or external databases, risking accidental deletion of external production databases.

## Decision

We introduce the **Universal Infrastructure Provisioning Engine V5** (`InfrastructureProvisionEngineV5`), **Provider Adapter Pattern** (`InfrastructureProviderAdapter`), and **Infrastructure Resource State Store** (`InfrastructureResourceStateStore`) with the following inviolable architecture rule:

> **Infrastructure providers execute immutable contracts only. Provider adapters are responsible solely for infrastructure lifecycle management. They must never inspect repositories, negotiate deployment intent, infer technologies, or perform application-level validation. Every provider (AWS, Docker, External, Kubernetes, Azure, GCP) must remain interchangeable through the InfrastructureProviderAdapter interface.**

### Key Principles

1. **Contract Execution Only** — The engine consumes immutable `InfrastructureContract` objects. It executes provisioning exactly as described and never infers or mutates intent.
2. **Generalized Resource Types** — Provisioning operates on generalized infrastructure concepts (`DATABASE`, `CACHE`, `QUEUE`, `SEARCH`, `OBJECT_STORAGE`, `FILE_STORAGE`, `LOAD_BALANCER`, `NETWORK`, `DNS`, `CERTIFICATE`, `COMPUTE`, `CONTAINER_RUNTIME`) rather than specific engine strings.
3. **Provider Adapter Registry** — Cloud providers implement `InfrastructureProviderAdapter` (`AWSProviderAdapter`, `DockerProviderAdapter`, `ExternalProviderAdapter`). The `InfrastructureProviderRegistry` auto-discovers adapters via Spring DI with zero switch statements.
4. **Terraform-Style Resource State Store** — `InfrastructureResourceStateStore` tracks every resource created by Deployrix (`InfrastructureResourceStateRecord`), recording `cloudId`, `ownership` (`PLATFORM`, `USER`, `EXTERNAL`), `createdAt`, and deletion policies.
5. **Ownership-Aware Rollback** — Rollbacks delete ONLY `PLATFORM` owned resources. `USER` or `EXTERNAL` owned resources are strictly preserved (`RETAIN` / `PRESERVE`).
6. **Pure Infrastructure Validation** — Validation checks infrastructure availability (e.g. AWS RDS status, Docker network existence) and NEVER application-level endpoints (TCP, HTTP, SQL, Mongo).

### Architecture

```
ExecutionGraph Node [infrastructure-node]
                 │
                 ▼
  InfrastructureProvisionEngineV5
                 │
                 ▼
  InfrastructureProviderRegistry ──► Auto-resolves Provider Adapter
                 │
                 ├─► AWSProviderAdapter (VPC, Security Groups, RDS, EC2, ALB, EFS)
                 ├─► DockerProviderAdapter (Networks, Volumes, Secrets)
                 └─► ExternalProviderAdapter (Verifies ownership, never provisions)
                 │
                 ▼
  InfrastructureResourceStateStore (Persists state, cloud IDs, & ownership)
                 │
                 ▼
  RuntimeInfrastructure & InfrastructureSnapshot
```

## Consequences

### Positive

- Complete separation of infrastructure provisioning from application negotiation and code inspection.
- Deterministic, zero-orphan resource cleanup during rollbacks.
- External and user-owned infrastructure is guaranteed safe from accidental destruction.
- Future providers (Kubernetes, Azure, GCP, Nomad) can be added simply by implementing `InfrastructureProviderAdapter`.

### Negative

- Every provisioned cloud resource must register a state record in `InfrastructureResourceStateStore`.

### Migration Path

Legacy infrastructure code continues to run behind feature flag `deployrix.runtime.infrastructure=v5`. When enabled, `InfrastructureModuleV5` delegates to `InfrastructureProvisionEngineV5`.
