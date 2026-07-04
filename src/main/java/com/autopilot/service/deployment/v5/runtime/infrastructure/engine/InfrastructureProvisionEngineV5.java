package com.autopilot.service.deployment.v5.runtime.infrastructure.engine;

import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.*;
import com.autopilot.service.deployment.v5.runtime.infrastructure.provider.InfrastructureProviderAdapter;
import com.autopilot.service.deployment.v5.runtime.infrastructure.provider.InfrastructureProviderRegistry;
import com.autopilot.service.deployment.v5.runtime.infrastructure.report.InfrastructureReports;
import com.autopilot.service.deployment.v5.runtime.infrastructure.snapshot.InfrastructureSnapshot;
import com.autopilot.service.deployment.v5.runtime.infrastructure.state.InfrastructureResourceStateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * The Universal Infrastructure Provisioning Engine.
 *
 * Executes infrastructure contracts only.
 * It NEVER inspects repositories, negotiates intent, classifies dependencies, or deploys applications.
 *
 * Feature flag gated by deployrix.runtime.infrastructure=v5
 *
 * @since V5.4 — ADR-008
 */
@Service
public class InfrastructureProvisionEngineV5 {

    private final InfrastructureProviderRegistry providerRegistry;
    private final InfrastructureResourceStateStore stateStore;

    @Value("${deployrix.runtime.infrastructure:v5}")
    private String infrastructureMode;

    public InfrastructureProvisionEngineV5(InfrastructureProviderRegistry providerRegistry,
                                           InfrastructureResourceStateStore stateStore) {
        this.providerRegistry = providerRegistry;
        this.stateStore = stateStore;
    }

    public boolean isV5Enabled() {
        return "v5".equalsIgnoreCase(infrastructureMode);
    }

    public ProvisioningResult provision(InfrastructureContract contract) {
        System.out.println("🧱 Infrastructure Provision Engine V5 — Provisioning contract: ["
                + contract.getId() + "] (Provider: " + contract.getProvider()
                + ", Type: " + contract.getResourceType() + ")");
        long start = System.currentTimeMillis();

        InfrastructureProviderAdapter adapter = providerRegistry.resolveAdapter(contract);

        try {
            RuntimeInfrastructure runtimeInfra = adapter.provision(contract, stateStore);
            boolean verified = adapter.verify(runtimeInfra);

            long duration = System.currentTimeMillis() - start;

            InfrastructureReports.InfrastructureProvisionReport provisionReport =
                    InfrastructureReports.InfrastructureProvisionReport.builder()
                            .resourceId(contract.getId())
                            .provider(adapter.providerId())
                            .success(verified)
                            .durationMs(duration)
                            .status(verified ? InfrastructureResourceLifecycle.READY : InfrastructureResourceLifecycle.FAILED)
                            .failureType(verified ? null : InfrastructureFailureType.VALIDATION_FAILED)
                            .logs(Collections.singletonList("Provisioning completed by " + adapter.providerId()))
                            .warnings(Collections.emptyList())
                            .build();

            InfrastructureReports.InfrastructureValidationReport validationReport =
                    InfrastructureReports.InfrastructureValidationReport.builder()
                            .resourceId(contract.getId())
                            .valid(verified)
                            .provider(adapter.providerId())
                            .statusMessage(verified ? "Infrastructure verified READY" : "Infrastructure validation failed")
                            .diagnostics(Collections.emptyList())
                            .build();

            InfrastructureSnapshot snapshot = adapter.snapshot(runtimeInfra);

            return new ProvisioningResult(runtimeInfra, provisionReport, validationReport, snapshot);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            System.err.println("❌ Infrastructure Provisioning failed for [" + contract.getId() + "]: " + e.getMessage());

            InfrastructureReports.InfrastructureProvisionReport failureReport =
                    InfrastructureReports.InfrastructureProvisionReport.builder()
                            .resourceId(contract.getId())
                            .provider(contract.getProvider())
                            .success(false)
                            .durationMs(duration)
                            .status(InfrastructureResourceLifecycle.FAILED)
                            .failureType(classifyFailure(e))
                            .logs(Collections.singletonList("Error: " + e.getMessage()))
                            .warnings(Collections.emptyList())
                            .build();

            throw new RuntimeException("Infrastructure provisioning failed for contract: " + contract.getId() + " - " + e.getMessage(), e);
        }
    }

    public InfrastructureReports.InfrastructureRollbackReport rollback(RuntimeInfrastructure runtimeInfra) {
        InfrastructureProviderAdapter adapter = providerRegistry.resolveAdapter(
                InfrastructureContract.builder().provider(runtimeInfra.getProvider()).build());
        return adapter.rollback(runtimeInfra, stateStore);
    }

    private InfrastructureFailureType classifyFailure(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("permission") || msg.contains("denied")) return InfrastructureFailureType.PERMISSION_DENIED;
        if (msg.contains("quota")) return InfrastructureFailureType.QUOTA_EXCEEDED;
        if (msg.contains("timeout")) return InfrastructureFailureType.TIMEOUT;
        if (msg.contains("network")) return InfrastructureFailureType.NETWORK_FAILURE;
        return InfrastructureFailureType.API_FAILURE;
    }

    @lombok.Value
    public static class ProvisioningResult {
        RuntimeInfrastructure runtimeInfrastructure;
        InfrastructureReports.InfrastructureProvisionReport provisionReport;
        InfrastructureReports.InfrastructureValidationReport validationReport;
        InfrastructureSnapshot snapshot;
    }
}
