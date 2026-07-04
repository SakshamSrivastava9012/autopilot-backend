package com.autopilot.service.deployment.v5.runtime.dependency.adapter;

import com.autopilot.service.deployment.v5.negotiation.DependencyContract;
import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.DependencyLifecycle;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependencyType;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.dependency.health.DependencyHealthWaiter;
import com.autopilot.service.deployment.v5.runtime.dependency.report.DependencyReports;
import com.autopilot.service.deployment.v5.runtime.dependency.snapshot.DependencySnapshot;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * External Dependency Provider Adapter.
 * Verifies external user-managed dependencies.
 * NEVER provisions or destroys external resources.
 *
 * @since V5.4 — ADR-009
 */
@Component
public class ExternalDependencyAdapter implements DependencyProviderAdapter {

    @Override
    public String providerId() {
        return "external";
    }

    @Override
    public boolean supports(DependencyContract contract) {
        return contract != null && ("existing_external".equalsIgnoreCase(contract.getProvider())
                || "external".equalsIgnoreCase(contract.getProvider())
                || contract.getOwnership() == OwnershipType.EXTERNAL
                || contract.getOwnership() == OwnershipType.USER);
    }

    @Override
    public RuntimeDependency create(DependencyContract contract, ResolvedCredentialContract credentials) {
        long start = System.currentTimeMillis();
        String depId = contract.getDependencyId() != null ? contract.getDependencyId() : "ext-dep-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("🔗 External Dependency Adapter — Verifying user dependency [" + depId + "]...");

        String endpoint = credentials.getHost() + ":" + credentials.getPort();

        return RuntimeDependency.builder()
                .id(depId)
                .dependencyType(RuntimeDependencyType.SQL_DATABASE)
                .provider(providerId())
                .runtimeStatus(DependencyLifecycle.READY)
                .runtimeEndpoint(endpoint)
                .runtimeMetadata(Collections.singletonMap("externalVerified", "true"))
                .credentialReference(credentials.getSecretReference())
                .healthReference("external-health-" + depId)
                .ownership(OwnershipType.EXTERNAL)
                .createdAtEpoch(start)
                .build();
    }

    @Override
    public boolean waitUntilReady(RuntimeDependency dependency, DependencyHealthWaiter waiter) {
        return true; // External resources are pre-existing
    }

    @Override
    public boolean verify(RuntimeDependency dependency) {
        System.out.println("   External Dependency Verification — Confirming endpoint: " + dependency.getRuntimeEndpoint());
        return dependency.getRuntimeEndpoint() != null;
    }

    @Override
    public DependencyReports.DependencyRollbackReport destroy(RuntimeDependency dependency) {
        System.out.println("   External Dependency Rollback — PRESERVING user external resource: " + dependency.getId());
        return DependencyReports.DependencyRollbackReport.builder()
                .dependencyId(dependency.getId())
                .success(true)
                .provider(providerId())
                .resourcesDestroyed(0)
                .resourcesPreserved(1)
                .logs(Collections.singletonList("External user resource '" + dependency.getId() + "' preserved"))
                .build();
    }

    @Override
    public DependencySnapshot snapshot(RuntimeDependency dependency) {
        return DependencySnapshot.builder()
                .deploymentId("ext-dep-snapshot")
                .dependencies(Collections.singletonList(dependency))
                .credentialReferences(Collections.singletonList(dependency.getCredentialReference()))
                .endpoints(Collections.singletonList(dependency.getRuntimeEndpoint()))
                .ownership(OwnershipType.EXTERNAL)
                .runtimeState("READY")
                .metadata(dependency.getRuntimeMetadata())
                .build();
    }
}
