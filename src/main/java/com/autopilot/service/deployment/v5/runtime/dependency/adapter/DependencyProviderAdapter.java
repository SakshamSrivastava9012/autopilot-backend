package com.autopilot.service.deployment.v5.runtime.dependency.adapter;

import com.autopilot.service.deployment.v5.negotiation.DependencyContract;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.dependency.health.DependencyHealthWaiter;
import com.autopilot.service.deployment.v5.runtime.dependency.report.DependencyReports;
import com.autopilot.service.deployment.v5.runtime.dependency.snapshot.DependencySnapshot;

/**
 * Interface for dependency provider adapters (Docker, AWS Managed, External, etc.).
 * Adapters are responsible solely for creating and verifying runtime dependency instances.
 *
 * @since V5.4 — ADR-009
 */
public interface DependencyProviderAdapter {

    String providerId();

    boolean supports(DependencyContract contract);

    RuntimeDependency create(DependencyContract contract, ResolvedCredentialContract credentials);

    boolean waitUntilReady(RuntimeDependency dependency, DependencyHealthWaiter waiter);

    boolean verify(RuntimeDependency dependency);

    DependencyReports.DependencyRollbackReport destroy(RuntimeDependency dependency);

    DependencySnapshot snapshot(RuntimeDependency dependency);
}
