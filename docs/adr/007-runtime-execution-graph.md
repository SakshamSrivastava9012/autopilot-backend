# ADR-007: Runtime Execution Graph & Modular Runtime Engine

## Status

Accepted

## Date

2026-07-03

## Context

The legacy deployment pipeline (`DeploymentPipelineService`) relied on hardcoded, imperative, sequential Java method calls to execute deployments (`provisionDatabase()` -> `createContainer()` -> `sleep()` -> `checkHealth()`). This introduced severe architectural rigidity:

1. **Tight Coupling** — Modifying step execution or adding new deployment capabilities required altering the central engine class.
2. **Sequential Bottlenecks** — Independent operations (e.g. provisioning Redis, MinIO, and RabbitMQ) were forced into serial execution instead of running concurrently.
3. **Imperative Rollback** — Error handling relied on ad-hoc `catch` blocks that frequently left orphaned cloud resources or containers running.
4. **Lack of Lifecycle Extensibility** — Adding target platforms (e.g. Kubernetes, AWS ECS, Service Mesh) required rewriting core engine loops.

## Decision

We introduce the **Universal Deployment Runtime Engine V5** (`DeploymentRuntimeEngineV5`) and **Graph-Driven Execution Model** (`ExecutionGraph`) with the following inviolable architecture rule:

> **The Deployment Runtime Engine is an orchestrator, not an executor of business logic. Every deployment capability (Infrastructure, Credentials, Containers, Startup, Reverse Proxy, Validation) must exist as an independent RuntimeModule connected only through immutable RuntimeContext and the ExecutionGraph. No RuntimeModule may directly invoke another RuntimeModule. All execution ordering must emerge from the ExecutionGraph rather than imperative method calls.**

### Key Principles

1. **Graph-Driven Execution** — Deployments are modeled as an immutable directed acyclic graph (`ExecutionGraph`). Every deployment step is represented by a stateless `ExecutionNode`.
2. **Cycle Prevention** — The `ExecutionGraphBuilder` performs topological sorting and cycle detection prior to execution. If a cycle exists, the deployment fails before executing any side effects.
3. **Decoupled Module Registry** — Capabilities are encapsulated as independent `RuntimeModule` implementations auto-discovered via Spring DI (`RuntimeModuleRegistry`).
4. **Isolated Shared Context** — Modules and nodes communicate strictly through `RuntimeContext`. Direct inter-module calls are forbidden.
5. **Deterministic Reverse Rollback** — When a node fails, rollback executes in reverse topological order for all completed dependencies (`RUNNING` -> `FAILED` -> `ROLLBACK` -> `ROLLED_BACK`). Unrelated successful branches remain unaffected where safe.
6. **Future-Proof Extensibility** — Adding Kubernetes, Service Mesh, or Multi-Cluster support requires only registering new `RuntimeModule` beans; the core `DeploymentRuntimeEngineV5` engine remains unchanged.

### Architecture

```
DeploymentManifest V5 (immutable contract)
              │
              ▼
  RuntimeModuleRegistry (Spring DI auto-discovery)
              │
              ▼
    ExecutionGraphBuilder ──► Topological Sort & Cycle Detection
              │
              ▼
    ExecutionGraph (immutable DAG)
              │
              ▼
    RuntimeScheduler ───────► Parallel Node Scheduling & State Machine
              │
              ├─► ExecutionPhases: INFRASTRUCTURE ➔ DEPENDENCIES ➔ CREDENTIALS ➔ CONTAINERS ➔ STARTUP ➔ PROXY ➔ VALIDATION
              ├─► RuntimeContext (Thread-safe shared state & contracts)
              └─► Reverse Topological Rollback on Failure
              │
              ▼
    RuntimeSnapshot & Structured Reports
```

## Consequences

### Positive

- Complete decoupling between orchestration and business logic capabilities.
- Parallel execution of independent deployment steps.
- Safe, automatic rollback in reverse dependency order upon node failure.
- Structured, real-time diagnostic reporting via `ExecutionTimeline` and `RuntimeSnapshot`.
- Legacy pipeline (`DeploymentPipelineService`) remains operational behind feature flag `deployrix.runtime.engine=v5`.

### Negative

- Every new deployment step must be declared as a `RuntimeModule` producing stateless `ExecutionNode` instances.

### Migration Path

The V5 Runtime Engine operates in parallel with the legacy `DeploymentPipelineService` controlled by `deployrix.runtime.engine=v5`. Legacy deployment calls transparently delegate to the graph-driven engine when the V5 flag is active.
