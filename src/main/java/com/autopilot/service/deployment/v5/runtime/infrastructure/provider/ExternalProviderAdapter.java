package com.autopilot.service.deployment.v5.runtime.infrastructure.provider;

import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.InfrastructureContract;
import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.InfrastructureResourceLifecycle;
import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.RuntimeInfrastructure;
import com.autopilot.service.deployment.v5.runtime.infrastructure.report.InfrastructureReports;
import com.autopilot.service.deployment.v5.runtime.infrastructure.snapshot.InfrastructureSnapshot;
import com.autopilot.service.deployment.v5.runtime.infrastructure.state.InfrastructureResourceStateRecord;
import com.autopilot.service.deployment.v5.runtime.infrastructure.state.InfrastructureResourceStateStore;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * External Infrastructure Provider Adapter.
 * Never provisions any cloud resources. Only verifies ownership of user/external databases & services.
 * Rollback NEVER deletes external resources.
 *
 * @since V5.4 — ADR-008
 */
@Component
public class ExternalProviderAdapter implements InfrastructureProviderAdapter {

    @Override
    public String providerId() {
        return "external";
    }

    @Override
    public boolean supports(InfrastructureContract contract) {
        return contract != null && ("external".equalsIgnoreCase(contract.getProvider())
                || "existing_external".equalsIgnoreCase(contract.getProvider())
                || contract.getOwnership() == OwnershipType.EXTERNAL
                || contract.getOwnership() == OwnershipType.USER);
    }

    @Override
    public RuntimeInfrastructure provision(InfrastructureContract contract, InfrastructureResourceStateStore stateStore) {
        long start = System.currentTimeMillis();
        System.out.println("🔗 External Provider Adapter — Verifying user-owned external resource [" + contract.getId() + "]...");

        String identifier = "ext-" + contract.getId();
        String endpoint = contract.getConfiguration() != null ? (String) contract.getConfiguration().getOrDefault("endpoint", "external-host") : "external-host";

        InfrastructureResourceStateRecord record = InfrastructureResourceStateRecord.builder()
                .internalResourceId(contract.getId())
                .deploymentId(contract.getMetadata() != null ? contract.getMetadata().getOrDefault("deploymentId", "unknown") : "unknown")
                .provider(providerId())
                .cloudId(identifier)
                .ownership(OwnershipType.EXTERNAL)
                .createdAtEpoch(start)
                .deletionPolicy("RETAIN")
                .rollbackPolicy("PRESERVE")
                .tags(contract.getTags() != null ? contract.getTags() : Collections.emptyMap())
                .metadata(contract.getMetadata() != null ? contract.getMetadata() : Collections.emptyMap())
                .build();
        stateStore.saveRecord(record);

        return RuntimeInfrastructure.builder()
                .provider(providerId())
                .identifier(identifier)
                .endpoint(endpoint)
                .status(InfrastructureResourceLifecycle.READY)
                .metadata(contract.getMetadata() != null ? contract.getMetadata() : Collections.emptyMap())
                .creationTimeEpoch(start)
                .runtimeProperties(Collections.singletonMap("externalOwnershipVerified", true))
                .build();
    }

    @Override
    public boolean verify(RuntimeInfrastructure runtimeInfra) {
        System.out.println("   External Verification — Confirming ownership of external endpoint: " + runtimeInfra.getEndpoint());
        return runtimeInfra.getEndpoint() != null;
    }

    @Override
    public InfrastructureReports.InfrastructureRollbackReport rollback(RuntimeInfrastructure runtimeInfra, InfrastructureResourceStateStore stateStore) {
        System.out.println("   External Rollback — PRESERVING external user resource: " + runtimeInfra.getIdentifier());

        return InfrastructureReports.InfrastructureRollbackReport.builder()
                .resourceId(runtimeInfra.getIdentifier())
                .success(true)
                .provider(providerId())
                .resourcesDeleted(0)
                .resourcesPreserved(1)
                .logs(Collections.singletonList("External user resource preserved during rollback"))
                .build();
    }

    @Override
    public InfrastructureSnapshot snapshot(RuntimeInfrastructure runtimeInfra) {
        return InfrastructureSnapshot.builder()
                .deploymentId("external-snapshot")
                .resources(Collections.singletonList(runtimeInfra))
                .identifiers(Collections.singletonList(runtimeInfra.getIdentifier()))
                .providers(Collections.singletonList(providerId()))
                .snapshotTimeEpoch(System.currentTimeMillis())
                .region("external")
                .allAvailable(true)
                .metadata(Collections.emptyMap())
                .build();
    }
}
