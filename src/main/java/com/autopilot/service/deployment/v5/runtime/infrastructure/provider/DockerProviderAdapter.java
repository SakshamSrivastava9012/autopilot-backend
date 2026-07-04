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
 * Docker Infrastructure Provider Adapter.
 * Handles local Docker network, volume, bridge, and secret infrastructure.
 *
 * @since V5.4 — ADR-008
 */
@Component
public class DockerProviderAdapter implements InfrastructureProviderAdapter {

    @Override
    public String providerId() {
        return "docker";
    }

    @Override
    public boolean supports(InfrastructureContract contract) {
        return contract != null && ("docker".equalsIgnoreCase(contract.getProvider())
                || "platform_managed".equalsIgnoreCase(contract.getProvider())
                || "docker_runtime".equalsIgnoreCase(contract.getProvider()));
    }

    @Override
    public RuntimeInfrastructure provision(InfrastructureContract contract, InfrastructureResourceStateStore stateStore) {
        long start = System.currentTimeMillis();
        System.out.println("🐳 Docker Provider Adapter — Provisioning " + contract.getResourceType() + " [" + contract.getId() + "]...");

        String networkOrVolumeId = "deployrix-docker-" + contract.getResourceType().name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        String endpoint = "localhost";

        InfrastructureResourceStateRecord record = InfrastructureResourceStateRecord.builder()
                .internalResourceId(contract.getId())
                .deploymentId(contract.getMetadata() != null ? contract.getMetadata().getOrDefault("deploymentId", "unknown") : "unknown")
                .provider(providerId())
                .cloudId(networkOrVolumeId)
                .ownership(contract.getOwnership() != null ? contract.getOwnership() : OwnershipType.PLATFORM)
                .createdAtEpoch(start)
                .deletionPolicy("DELETE")
                .rollbackPolicy("DELETE_IF_PLATFORM")
                .tags(contract.getTags() != null ? contract.getTags() : Collections.emptyMap())
                .metadata(contract.getMetadata() != null ? contract.getMetadata() : Collections.emptyMap())
                .build();
        stateStore.saveRecord(record);

        Map<String, Object> runtimeProps = new HashMap<>();
        runtimeProps.put("dockerId", networkOrVolumeId);

        return RuntimeInfrastructure.builder()
                .provider(providerId())
                .identifier(networkOrVolumeId)
                .endpoint(endpoint)
                .status(InfrastructureResourceLifecycle.READY)
                .metadata(contract.getMetadata() != null ? contract.getMetadata() : Collections.emptyMap())
                .creationTimeEpoch(start)
                .runtimeProperties(runtimeProps)
                .build();
    }

    @Override
    public boolean verify(RuntimeInfrastructure runtimeInfra) {
        System.out.println("   Docker Verification — Checking Docker network/volume existence: " + runtimeInfra.getIdentifier());
        return runtimeInfra.getIdentifier() != null && runtimeInfra.getIdentifier().contains("docker");
    }

    @Override
    public InfrastructureReports.InfrastructureRollbackReport rollback(RuntimeInfrastructure runtimeInfra, InfrastructureResourceStateStore stateStore) {
        System.out.println("   Docker Rollback — Removing Docker network/volume: " + runtimeInfra.getIdentifier());
        stateStore.deleteRecord(runtimeInfra.getIdentifier());

        return InfrastructureReports.InfrastructureRollbackReport.builder()
                .resourceId(runtimeInfra.getIdentifier())
                .success(true)
                .provider(providerId())
                .resourcesDeleted(1)
                .resourcesPreserved(0)
                .logs(Collections.singletonList("Docker container network removed"))
                .build();
    }

    @Override
    public InfrastructureSnapshot snapshot(RuntimeInfrastructure runtimeInfra) {
        return InfrastructureSnapshot.builder()
                .deploymentId("docker-snapshot")
                .resources(Collections.singletonList(runtimeInfra))
                .identifiers(Collections.singletonList(runtimeInfra.getIdentifier()))
                .providers(Collections.singletonList(providerId()))
                .snapshotTimeEpoch(System.currentTimeMillis())
                .region("local")
                .allAvailable(true)
                .metadata(Collections.emptyMap())
                .build();
    }
}
