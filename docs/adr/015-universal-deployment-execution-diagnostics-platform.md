# ADR-015: Universal Deployment Execution, Live Diagnostics & Adaptive Timeout Platform

## Status
Accepted

## Context
Deployrix V5 provides deterministic execution graph scheduling, dependency negotiation, environment injection, startup negotiation, and multi-layered verification. However, during execution, long-running remote operations (such as container image builds, AWS SSM command executions, and multi-layer Docker image pulls) suffered from limited visibility and rigid timeout mechanisms.

Deployments previously relied on fixed 600-second timeouts regardless of image size or active stream progress. In addition, execution diagnostics and log streaming were unstructured, making stalled deployments difficult to distinguish from active, resource-heavy operations.

## Decision
We introduce the **Universal Deployment Execution, Live Diagnostics & Adaptive Timeout Platform** (`v5.runtime.execution`) as a first-class operational observation layer above the V5 Runtime Engine.

### Architectural Principles
1. **Observable Execution**: Every stage streams live logs and structured immutable events tied directly to a `DeploymentSession`.
2. **Progress-Driven Health**: Deployment progress, not arbitrary elapsed clock time, determines health. Fixed timeouts are replaced by `AdaptiveTimeoutManager` and `DeploymentStallDetector`.
3. **Provider-Agnostic Diagnostics**: Diagnostics observe runtime stdout/stderr, container logs, layer progress, and health metrics without performing infrastructure logic.
4. **Diagnostic Reports**: Generates structured, immutable reports (`DeploymentExecutionReport`, `DeploymentMetricsReport`, `DeploymentDiagnosticsReport`, `ImageOptimizationReport`, `DeploymentProgressReport`, `DeploymentLogReport`) bound to `DeploymentSession`.

## Consequences
- **Zero Opaque Execution**: Real-time event streaming and layer-level Docker pull progress updates continuously update the operational dashboard.
- **Elimination of Arbitrary Failures**: Stalls are detected only when zero stdout, stderr, or progress events occur for a configurable stall threshold (default: 180s), while large images/builds are granted context-aware adaptive timeouts.
- **Actionable Optimization Recommendations**: Automatically analyzes container image layer sizes and suggests multi-stage build optimizations.
