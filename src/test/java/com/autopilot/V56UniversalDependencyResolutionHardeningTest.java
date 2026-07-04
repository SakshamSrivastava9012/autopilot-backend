package com.autopilot;

import com.autopilot.dto.DeploymentManifest;
import com.autopilot.service.deployment.v5.negotiation.DependencyContract;
import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependencyType;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.DependencyLifecycle;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.dependency.discovery.RuntimeDiscoveryEngine;
import com.autopilot.service.deployment.v5.runtime.environment.resolver.RuntimeConnectionContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test suite for Deployrix V5.6 Universal Dependency Resolution and Hardening.
 */
@DisplayName("Deployrix V5.6 — Universal Dependency Resolution & Hardening Suite")
public class V56UniversalDependencyResolutionHardeningTest {

    @Test
    @DisplayName("RuntimeDiscoveryEngine correctly observes Docker runtime dependency and builds Contract & Report")
    void testRuntimeDiscoveryDocker() {
        RuntimeDiscoveryEngine engine = new RuntimeDiscoveryEngine();

        RuntimeDependency dependency = RuntimeDependency.builder()
                .id("mongo-primary")
                .provider("DOCKER_RUNTIME")
                .dependencyType(RuntimeDependencyType.NOSQL_DATABASE)
                .runtimeEndpoint("10.0.1.5:27017")
                .ownership(OwnershipType.PLATFORM)
                .runtimeStatus(DependencyLifecycle.HEALTHY)
                .healthReference("MONGO_PING")
                .runtimeMetadata(Map.of("ssl", "true", "authMechanism", "SCRAM-SHA-256"))
                .build();

        ResolvedCredentialContract credentials = ResolvedCredentialContract.builder()
                .host("10.0.1.5")
                .port(27017)
                .database("appdb")
                .username("dbuser")
                .password("securepassword")
                .build();

        DependencyContract contract = DependencyContract.builder()
                .dependencyId("mongo-primary")
                .type("mongodb")
                .provider("docker")
                .build();

        RuntimeDiscoveryEngine.RuntimeDiscoveryResult result = engine.discover(dependency, credentials, contract);
        RuntimeConnectionContract connContract = result.getConnectionContract();
        RuntimeDiscoveryEngine.RuntimeDiscoveryReport report = result.getDiscoveryReport();

        // Validate contract
        assertEquals("conn-mongo-primary", connContract.getConnectionId());
        assertEquals("mongo-primary", connContract.getDependencyId());
        assertEquals("NOSQL_DATABASE", connContract.getDependencyType());
        assertEquals("mongodb", connContract.getProtocol());
        assertEquals("10.0.1.5", connContract.getHost());
        assertEquals(27017, connContract.getPort());
        assertEquals("dbuser", connContract.getUsername());
        assertEquals("securepassword", connContract.getPassword());
        assertEquals("appdb", connContract.getDatabase());
        assertTrue(connContract.isSsl());
        assertEquals("SCRAM-SHA-256", connContract.getAuthentication());
        assertEquals("MONGO_PING", connContract.getHealthEndpoint());
        assertEquals("mongodb://dbuser:securepassword@10.0.1.5:27017/appdb", connContract.getUri());

        // Validate report
        assertEquals("mongo-primary", report.getDependencyId());
        assertEquals("10.0.1.5", report.getDiscoveredHost());
        assertEquals(27017, report.getDiscoveredPort());
        assertEquals("mongodb", report.getDiscoveredProtocol());
        assertTrue(report.isSslEnabled());
        assertEquals("SCRAM-SHA-256", report.getAuthenticationMechanism());
        assertEquals("mongodb://dbuser:securepassword@10.0.1.5:27017/appdb", report.getConnectionUri());
    }

    @Test
    @DisplayName("RuntimeDiscoveryEngine correctly resolves postgresql native protocol URI")
    void testRuntimeDiscoveryPostgres() {
        RuntimeDiscoveryEngine engine = new RuntimeDiscoveryEngine();

        RuntimeDependency dependency = RuntimeDependency.builder()
                .id("pg-db")
                .provider("aws_rds")
                .dependencyType(RuntimeDependencyType.SQL_DATABASE)
                .runtimeEndpoint("rds.host:5432")
                .ownership(OwnershipType.PLATFORM)
                .runtimeStatus(DependencyLifecycle.HEALTHY)
                .build();

        ResolvedCredentialContract credentials = ResolvedCredentialContract.builder()
                .host("rds.host")
                .port(5432)
                .database("proddb")
                .username("postgres")
                .password("secretpass")
                .build();

        var result = engine.discover(dependency, credentials, null);
        assertEquals("jdbc:postgresql://rds.host:5432/proddb", result.getConnectionContract().getUri());
        assertEquals("postgresql", result.getConnectionContract().getProtocol());
    }
}
