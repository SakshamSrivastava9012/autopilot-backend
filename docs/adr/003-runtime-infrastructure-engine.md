# ADR 003: Runtime Infrastructure Engine

## Status
Accepted

## Context
Deployments frequently fail due to transient infrastructure issues: the Docker daemon is slow to start, `cloud-init` hasn't finished provisioning, bridge networks are corrupted, or the Docker registry times out. Currently, Deployrix executes deployment steps linearly, blindly assuming the underlying VM and Docker socket are fully ready. When an underlying infrastructure component is unhealthy, Deployrix throws generic failures (e.g., "Connection refused"), masking the true root cause and failing deployments that could have otherwise succeeded after a brief wait or automated recovery.

## Decision
We are introducing the **Runtime Infrastructure Engine** as the absolute first barrier in the deployment lifecycle.

1. **Deterministic State Machine:** A deployment will not proceed until a rigorous state machine (EC2 Provisioned -> Cloud-init -> System -> Docker -> Containerd -> Networking -> Registry -> Filesystem) is fully satisfied.
2. **Deep Verification:** The engine will verify `docker.sock`, check `docker info`, inspect `overlay2` storage, and perform a live test (`docker pull hello-world` && `docker run hello-world`).
3. **Self-Healing:** If any check fails, the engine will attempt to remediate the issue via exponential backoff, automated daemon restarts (`systemctl restart docker`), and network regeneration rather than immediately failing the deployment.
4. **Structured Reporting:** All findings will be aggregated into a `RuntimeInfrastructureReport`. "Dockerd never became ready" is no longer an acceptable end state without deep diagnostics.

## Consequences
- **Positive:** False-positive deployment failures caused by infrastructure races will drop to near zero.
- **Positive:** When infrastructure does fatally fail, the user receives precise diagnostics (e.g., "Docker overlay2 storage corrupted") instead of a generic timeout.
- **Negative:** Initial deployment times will slightly increase due to the thorough pre-flight validation checks (hello-world pull).
- **Migration Strategy:** The `RuntimeInfrastructureModule` will be injected at the very beginning of the modular execution pipeline, acting as a gatekeeper before any manifest building or repository discovery occurs.
