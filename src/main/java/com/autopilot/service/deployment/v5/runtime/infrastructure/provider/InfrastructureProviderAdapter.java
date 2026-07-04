package com.autopilot.service.deployment.v5.runtime.infrastructure.provider;

import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.InfrastructureContract;
import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.RuntimeInfrastructure;
import com.autopilot.service.deployment.v5.runtime.infrastructure.report.InfrastructureReports;
import com.autopilot.service.deployment.v5.runtime.infrastructure.snapshot.InfrastructureSnapshot;
import com.autopilot.service.deployment.v5.runtime.infrastructure.state.InfrastructureResourceStateStore;

/**
 * Provider adapter interface for infrastructure lifecycle management.
 * Adapters are responsible solely for execution — never negotiation or inspection.
 *
 * @since V5.4 — ADR-008
 */
public interface InfrastructureProviderAdapter {

    String providerId();

    boolean supports(InfrastructureContract contract);

    RuntimeInfrastructure provision(InfrastructureContract contract, InfrastructureResourceStateStore stateStore);

    boolean verify(RuntimeInfrastructure runtimeInfra);

    InfrastructureReports.InfrastructureRollbackReport rollback(RuntimeInfrastructure runtimeInfra, InfrastructureResourceStateStore stateStore);

    InfrastructureSnapshot snapshot(RuntimeInfrastructure runtimeInfra);
}
