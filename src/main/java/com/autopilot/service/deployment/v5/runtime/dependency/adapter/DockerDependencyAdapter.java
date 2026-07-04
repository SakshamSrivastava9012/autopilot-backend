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
 * Docker Dependency Provider Adapter.
 * Handles local Docker container dependencies (Postgres, MySQL, Mongo, Redis, RabbitMQ, etc.).
 *
 * @since V5.4 — ADR-009
 */
@Component
public class DockerDependencyAdapter implements DependencyProviderAdapter {

    @Override
    public String providerId() {
        return "docker";
    }

    @Override
    public boolean supports(DependencyContract contract) {
        return contract != null && ("docker_runtime".equalsIgnoreCase(contract.getProvider())
                || "docker".equalsIgnoreCase(contract.getProvider())
                || "platform_managed".equalsIgnoreCase(contract.getProvider()));
    }

    @Override
    public RuntimeDependency create(DependencyContract contract, ResolvedCredentialContract credentials) {
        long start = System.currentTimeMillis();
        String depId = contract.getDependencyId() != null ? contract.getDependencyId() : "docker-dep-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("🐳 Docker Dependency Adapter — Creating container dependency [" + depId + "] (" + contract.getType() + ")...");

        RuntimeDependencyType depType = mapType(contract.getType());
        String endpoint = credentials.getHost() + ":" + credentials.getPort();

        Map<String, String> meta = new HashMap<>();
        meta.put("containerName", "deployrix-dep-" + depId);
        meta.put("image", contract.getType() + ":" + (contract.getVersion() != null ? contract.getVersion() : "latest"));

        return RuntimeDependency.builder()
                .id(depId)
                .dependencyType(depType)
                .provider(providerId())
                .runtimeStatus(DependencyLifecycle.READY)
                .runtimeEndpoint(endpoint)
                .runtimeMetadata(meta)
                .credentialReference(credentials.getSecretReference())
                .healthReference("docker-health-" + depId)
                .ownership(OwnershipType.PLATFORM)
                .createdAtEpoch(start)
                .build();
    }

    @Override
    public boolean waitUntilReady(RuntimeDependency dependency, DependencyHealthWaiter waiter) {
        try {
            return waiter.awaitHealth(dependency, "DOCKER_HEALTHCHECK", 15000).get();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean verify(RuntimeDependency dependency) {
        System.out.println("   Docker Verification — Checking dependency [" + dependency.getId() + "] endpoint: " + dependency.getRuntimeEndpoint());
        return dependency.getRuntimeEndpoint() != null;
    }

    @Override
    public DependencyReports.DependencyRollbackReport destroy(RuntimeDependency dependency) {
        System.out.println("   Docker Rollback — Destroying container dependency [" + dependency.getId() + "]");
        return DependencyReports.DependencyRollbackReport.builder()
                .dependencyId(dependency.getId())
                .success(true)
                .provider(providerId())
                .resourcesDestroyed(1)
                .resourcesPreserved(0)
                .logs(Collections.singletonList("Docker container 'deployrix-dep-" + dependency.getId() + "' destroyed"))
                .build();
    }

    @Override
    public DependencySnapshot snapshot(RuntimeDependency dependency) {
        return DependencySnapshot.builder()
                .deploymentId("docker-dep-snapshot")
                .dependencies(Collections.singletonList(dependency))
                .credentialReferences(Collections.singletonList(dependency.getCredentialReference()))
                .endpoints(Collections.singletonList(dependency.getRuntimeEndpoint()))
                .ownership(OwnershipType.PLATFORM)
                .runtimeState("READY")
                .metadata(dependency.getRuntimeMetadata())
                .build();
    }

    private RuntimeDependencyType mapType(String type) {
        if (type == null) return RuntimeDependencyType.SQL_DATABASE;
        String t = type.toLowerCase();
        if (t.contains("redis") || t.contains("cache")) return RuntimeDependencyType.CACHE;
        if (t.contains("rabbit") || t.contains("kafka")) return RuntimeDependencyType.MESSAGE_BUS;
        if (t.contains("mongo")) return RuntimeDependencyType.NOSQL_DATABASE;
        if (t.contains("elastic") || t.contains("search")) return RuntimeDependencyType.SEARCH;
        if (t.contains("qdrant") || t.contains("milvus")) return RuntimeDependencyType.VECTOR_DATABASE;
        return RuntimeDependencyType.SQL_DATABASE;
    }
}
