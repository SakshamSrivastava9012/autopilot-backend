package com.autopilot.service.deployment.v5.runtime.dependency.engine;

import com.autopilot.service.deployment.v5.negotiation.DependencyContract;
import com.autopilot.service.deployment.v5.runtime.dependency.adapter.DependencyProviderAdapter;
import com.autopilot.service.deployment.v5.runtime.dependency.adapter.DependencyProviderRegistry;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.DependencyFailureType;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.DependencyLifecycle;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.CredentialResolver;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.dependency.health.DependencyHealthWaiter;
import com.autopilot.service.deployment.v5.runtime.dependency.report.DependencyReports;
import com.autopilot.service.deployment.v5.runtime.dependency.snapshot.DependencySnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * The Universal Dependency Provision Engine.
 *
 * Turns abstract dependency contracts into running runtime dependency instances.
 * Supports multiple instances of the same dependency type keyed by unique dependency IDs.
 *
 * It NEVER negotiates providers, inspects repositories, infers frameworks, or deploys applications.
 *
 * Feature flag gated by deployrix.runtime.dependency=v5
 *
 * @since V5.4 — ADR-009
 */
@Service
public class DependencyProvisionEngineV5 {

    private final DependencyProviderRegistry providerRegistry;
    private final CredentialResolver credentialResolver;
    private final DependencyHealthWaiter healthWaiter;

    @Value("${deployrix.runtime.dependency:v5}")
    private String dependencyEngineMode;

    public DependencyProvisionEngineV5(DependencyProviderRegistry providerRegistry,
                                         CredentialResolver credentialResolver,
                                         DependencyHealthWaiter healthWaiter) {
        this.providerRegistry = providerRegistry;
        this.credentialResolver = credentialResolver;
        this.healthWaiter = healthWaiter;
    }

    public boolean isV5Enabled() {
        return "v5".equalsIgnoreCase(dependencyEngineMode);
    }

    /**
     * Provision a single dependency contract.
     */
    public ProvisioningResult provision(DependencyContract contract) {
        String depId = contract.getDependencyId() != null ? contract.getDependencyId() : "dep-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("🗄️ Dependency Provision Engine V5 — Provisioning dependency [" + depId + "] (Type: "
                + contract.getType() + ", Provider: " + contract.getProvider() + ")...");
        long start = System.currentTimeMillis();

        try {
            // 1. Resolve credentials (never invented, from provider or config)
            ResolvedCredentialContract credentials = credentialResolver.resolve(contract);

            // 2. Resolve provider adapter
            DependencyProviderAdapter adapter = providerRegistry.resolveAdapter(contract);

            // 3. Create runtime dependency
            RuntimeDependency runtimeDependency = adapter.create(contract, credentials);

            // 4. Wait for event-driven health readiness (no Thread.sleep)
            boolean ready = adapter.waitUntilReady(runtimeDependency, healthWaiter);

            long duration = System.currentTimeMillis() - start;

            // 5. Build structured reports
            DependencyReports.DependencyProvisionReport provReport = DependencyReports.DependencyProvisionReport.builder()
                    .dependencyId(depId)
                    .provider(adapter.providerId())
                    .success(ready)
                    .durationMs(duration)
                    .status(ready ? DependencyLifecycle.HEALTHY : DependencyLifecycle.FAILED)
                    .failureType(ready ? null : DependencyFailureType.DEPENDENCY_HEALTH_TIMEOUT)
                    .logs(Collections.singletonList("Provisioned by " + adapter.providerId()))
                    .warnings(Collections.emptyList())
                    .build();

            DependencyReports.DependencyHealthReport healthReport = DependencyReports.DependencyHealthReport.builder()
                    .dependencyId(depId)
                    .healthy(ready)
                    .healthStrategy(runtimeDependency.getHealthReference())
                    .responseTimeMs(duration)
                    .diagnostics(Collections.emptyList())
                    .build();

            DependencyReports.CredentialResolutionReport credReport = DependencyReports.CredentialResolutionReport.builder()
                    .dependencyId(depId)
                    .provider(credentials.getProvider())
                    .generatedBy(credentials.getGeneratedBy())
                    .rotationSupported(credentials.isRotationSupported())
                    .secretReference(credentials.getSecretReference())
                    .build();

            DependencySnapshot snapshot = adapter.snapshot(runtimeDependency);

            return new ProvisioningResult(depId, runtimeDependency, credentials, provReport, healthReport, credReport, snapshot);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            System.err.println("❌ Dependency Provisioning failed for [" + depId + "]: " + e.getMessage());

            DependencyReports.DependencyProvisionReport failureReport = DependencyReports.DependencyProvisionReport.builder()
                    .dependencyId(depId)
                    .provider(contract.getProvider())
                    .success(false)
                    .durationMs(duration)
                    .status(DependencyLifecycle.FAILED)
                    .failureType(classifyFailure(e))
                    .logs(Collections.singletonList("Error: " + e.getMessage()))
                    .warnings(Collections.emptyList())
                    .build();

            throw new RuntimeException("Dependency provisioning failed for [" + depId + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Provision multiple dependency contracts (e.g. primary-db, analytics-db, session-cache).
     */
    public Map<String, ProvisioningResult> provisionAll(List<DependencyContract> contracts) {
        Map<String, ProvisioningResult> results = new LinkedHashMap<>();
        if (contracts != null) {
            for (DependencyContract contract : contracts) {
                ProvisioningResult res = provision(contract);
                results.put(res.getDependencyId(), res);
            }
        }
        return Collections.unmodifiableMap(results);
    }

    public DependencyReports.DependencyRollbackReport destroy(RuntimeDependency dependency) {
        DependencyProviderAdapter adapter = providerRegistry.resolveAdapter(
                DependencyContract.builder().provider(dependency.getProvider()).build());
        return adapter.destroy(dependency);
    }

    private DependencyFailureType classifyFailure(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("timeout")) return DependencyFailureType.DEPENDENCY_HEALTH_TIMEOUT;
        if (msg.contains("credential") || msg.contains("auth")) return DependencyFailureType.CREDENTIAL_NOT_AVAILABLE;
        if (msg.contains("version")) return DependencyFailureType.VERSION_UNSUPPORTED;
        return DependencyFailureType.PROVIDER_ERROR;
    }

    @lombok.Value
    public static class ProvisioningResult {
        String dependencyId;
        RuntimeDependency runtimeDependency;
        ResolvedCredentialContract credentials;
        DependencyReports.DependencyProvisionReport provisionReport;
        DependencyReports.DependencyHealthReport healthReport;
        DependencyReports.CredentialResolutionReport credentialReport;
        DependencySnapshot snapshot;
    }
}
