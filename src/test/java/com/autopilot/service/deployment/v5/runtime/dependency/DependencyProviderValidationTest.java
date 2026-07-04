package com.autopilot.service.deployment.v5.runtime.dependency;

import com.autopilot.dto.DeploymentManifest;
import com.autopilot.service.deployment.v5.negotiation.DependencyContract;
import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.DependencyLifecycle;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependencyType;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.dependency.validation.DependencyValidationReport;
import com.autopilot.service.deployment.v5.runtime.dependency.validation.DependencyValidator;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionGraph;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionGraphBuilder;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;

import com.autopilot.service.deployment.v5.runtime.adapter.DependencyModuleV5;
import com.autopilot.service.deployment.v5.runtime.adapter.DependencyValidationModuleV5;
import com.autopilot.service.deployment.v5.runtime.adapter.CredentialModuleV5;
import com.autopilot.service.deployment.v5.runtime.adapter.InfrastructureModuleV5;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression test suite verifying ADR-009 and ADR-010 compliance:
 * - Docker and RDS providers are NEVER validated before the underlying resource exists.
 * - Validation operates on RuntimeDependency, not DependencyContract.
 * - Execution graph dynamically branches based on provider type.
 */
class DependencyProviderValidationTest {

    private DependencyValidator validator;
    private ExecutionGraphBuilder graphBuilder;

    @BeforeEach
    void setUp() {
        validator = new DependencyValidator();
        graphBuilder = new ExecutionGraphBuilder();
    }

    @Test
    @DisplayName("Docker MySQL (DOCKER_RUNTIME) — Pre-flight validation deferred, post-provision operates on RuntimeDependency")
    void testDockerMysqlValidationFlow() {
        DependencyContract contract = DependencyContract.builder()
                .dependencyId("mysql-db")
                .type("mysql")
                .provider("DOCKER_RUNTIME")
                .host("autopilot-mysql")
                .port(3306)
                .build();

        // 1. Pre-flight check MUST NOT validate TCP/DNS against unprovisioned hostname
        DependencyValidationReport preReport = validator.validate(contract, null, null);
        assertFalse(preReport.isValidated(), "Pre-flight validation must NOT pass before provisioning");
        assertTrue(preReport.isDeferred(), "Pre-flight validation must be marked DEFERRED for DOCKER_RUNTIME");
        assertEquals("PRE_FLIGHT_DEFERRED", preReport.getValidationPhase());

        // 2. Post-provision validation operates on RuntimeDependency
        RuntimeDependency runtimeDep = RuntimeDependency.builder()
                .id("mysql-db")
                .dependencyType(RuntimeDependencyType.SQL_DATABASE)
                .provider("DOCKER_RUNTIME")
                .runtimeStatus(DependencyLifecycle.HEALTHY)
                .runtimeEndpoint("127.0.0.1:3306")
                .build();

        ResolvedCredentialContract creds = ResolvedCredentialContract.builder()
                .secretReference("secret/mysql")
                .provider("DOCKER_RUNTIME")
                .build();

        DependencyValidationReport postReport = validator.validate(contract, runtimeDep, creds);
        assertTrue(postReport.isValidated(), "Post-provision validation MUST succeed against live RuntimeDependency");
        assertFalse(postReport.isDeferred(), "Post-provision validation must NOT be deferred");
        assertEquals("POST_PROVISION", postReport.getValidationPhase());
        assertEquals("127.0.0.1:3306", postReport.getEndpointValidated());
    }

    @Test
    @DisplayName("Docker MongoDB (DOCKER_RUNTIME) — Validation deferred until RuntimeDependency is available")
    void testDockerMongoValidationFlow() {
        DependencyContract contract = DependencyContract.builder()
                .dependencyId("mongo-cache")
                .type("mongodb")
                .provider("DOCKER_RUNTIME")
                .host("autopilot-mongo")
                .port(27017)
                .build();

        DependencyValidationReport preReport = validator.validate(contract, null, null);
        assertTrue(preReport.isDeferred(), "Docker MongoDB pre-flight validation must be DEFERRED");

        RuntimeDependency runtimeDep = RuntimeDependency.builder()
                .id("mongo-cache")
                .dependencyType(RuntimeDependencyType.NOSQL_DATABASE)
                .provider("DOCKER_RUNTIME")
                .runtimeStatus(DependencyLifecycle.HEALTHY)
                .runtimeEndpoint("127.0.0.1:27017")
                .build();

        DependencyValidationReport postReport = validator.validate(contract, runtimeDep, null);
        assertTrue(postReport.isValidated());
        assertEquals("POST_PROVISION", postReport.getValidationPhase());
    }

    @Test
    @DisplayName("AWS RDS (PLATFORM_MANAGED) — Provision -> wait available -> discover endpoint -> validate")
    void testAwsRdsValidationFlow() {
        DependencyContract contract = DependencyContract.builder()
                .dependencyId("rds-postgres")
                .type("postgresql")
                .provider("AWS_RDS")
                .host("rds-endpoint-placeholder")
                .port(5432)
                .build();

        // Must deferred pre-flight
        DependencyValidationReport preReport = validator.validate(contract, null, null);
        assertTrue(preReport.isDeferred(), "AWS RDS pre-flight validation must be DEFERRED");

        // Provisioned RDS instance
        RuntimeDependency rdsDep = RuntimeDependency.builder()
                .id("rds-postgres")
                .dependencyType(RuntimeDependencyType.SQL_DATABASE)
                .provider("AWS_RDS")
                .runtimeStatus(DependencyLifecycle.HEALTHY)
                .runtimeEndpoint("db-instance.c123456.us-east-1.rds.amazonaws.com:5432")
                .build();

        DependencyValidationReport postReport = validator.validate(contract, rdsDep, null);
        assertTrue(postReport.isValidated());
        assertEquals("db-instance.c123456.us-east-1.rds.amazonaws.com:5432", postReport.getEndpointValidated());
    }

    @Test
    @DisplayName("MongoDB Atlas (EXISTING_EXTERNAL) — Pre-flight validation executes BEFORE provisioning")
    void testMongoAtlasValidationFlow() {
        DependencyContract contract = DependencyContract.builder()
                .dependencyId("atlas-cluster")
                .type("mongodb")
                .provider("EXISTING_EXTERNAL")
                .host("cluster0.mongodb.net")
                .port(27017)
                .build();

        DependencyValidationReport preReport = validator.validate(contract, null, null);
        assertTrue(preReport.isValidated(), "EXISTING_EXTERNAL must validate pre-flight");
        assertFalse(preReport.isDeferred());
        assertEquals("PRE_FLIGHT", preReport.getValidationPhase());
        assertEquals("cluster0.mongodb.net:27017", preReport.getEndpointValidated());
    }

    @Test
    @DisplayName("External PostgreSQL (EXISTING_EXTERNAL) — Pre-flight validation executes BEFORE provisioning")
    void testExternalPostgresValidationFlow() {
        DependencyContract contract = DependencyContract.builder()
                .dependencyId("ext-pg")
                .type("postgresql")
                .provider("EXISTING_EXTERNAL")
                .host("db.external-cloud.com")
                .port(5432)
                .build();

        DependencyValidationReport preReport = validator.validate(contract, null, null);
        assertTrue(preReport.isValidated());
        assertEquals("PRE_FLIGHT", preReport.getValidationPhase());
    }

    @Test
    @DisplayName("ExecutionGraph Topological Sort — DOCKER_RUNTIME validation node depends on dependency node")
    void testExecutionGraphTopologyForDockerRuntime() {
        RuntimeContext context = new RuntimeContext("dep-1", DeploymentManifest.builder().build(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), null);
        context.putResolvedObject("TargetProvider", "DOCKER_RUNTIME");

        DependencyValidationModuleV5 validationModule = new DependencyValidationModuleV5(validator);

        ExecutionNode infraNode = mock(ExecutionNode.class);
        when(infraNode.getId()).thenReturn("infrastructure-node");
        when(infraNode.dependsOn()).thenReturn(Collections.emptyList());

        ExecutionNode depNode = mock(ExecutionNode.class);
        when(depNode.getId()).thenReturn("dependency-node");
        when(depNode.dependsOn()).thenReturn(Collections.singletonList("infrastructure-node"));

        ExecutionNode valNode = validationModule.createNode(context);
        assertEquals("dependency-validation-node", valNode.getId());
        assertEquals(Collections.singletonList("dependency-node"), valNode.dependsOn(), "DOCKER_RUNTIME validation node MUST depend on dependency-node");

        ExecutionNode credNode = mock(ExecutionNode.class);
        when(credNode.getId()).thenReturn("credential-node");
        when(credNode.dependsOn()).thenReturn(Collections.singletonList("dependency-validation-node"));

        List<ExecutionNode> nodes = Arrays.asList(infraNode, depNode, valNode, credNode);
        ExecutionGraph graph = graphBuilder.build(nodes);

        List<String> order = graph.getTopologicalOrder();
        assertTrue(order.indexOf("dependency-node") < order.indexOf("dependency-validation-node"), "dependency-node MUST execute BEFORE dependency-validation-node for DOCKER_RUNTIME");
        assertTrue(order.indexOf("dependency-validation-node") < order.indexOf("credential-node"), "dependency-validation-node MUST execute BEFORE credential-node");
    }

    @Test
    @DisplayName("ExecutionGraph Topological Sort — EXISTING_EXTERNAL validation node runs BEFORE dependency node")
    void testExecutionGraphTopologyForExistingExternal() {
        RuntimeContext context = new RuntimeContext("dep-2", DeploymentManifest.builder().build(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), null);
        context.putResolvedObject("TargetProvider", "EXISTING_EXTERNAL");

        DependencyValidationModuleV5 validationModule = new DependencyValidationModuleV5(validator);

        ExecutionNode infraNode = mock(ExecutionNode.class);
        when(infraNode.getId()).thenReturn("infrastructure-node");
        when(infraNode.dependsOn()).thenReturn(Collections.emptyList());

        ExecutionNode valNode = validationModule.createNode(context);
        assertEquals("dependency-validation-node", valNode.getId());
        assertEquals(Collections.singletonList("infrastructure-node"), valNode.dependsOn(), "EXISTING_EXTERNAL validation node MUST depend on infrastructure-node");

        ExecutionNode depNode = mock(ExecutionNode.class);
        when(depNode.getId()).thenReturn("dependency-node");
        when(depNode.dependsOn()).thenReturn(Collections.singletonList("dependency-validation-node"));

        ExecutionNode credNode = mock(ExecutionNode.class);
        when(credNode.getId()).thenReturn("credential-node");
        when(credNode.dependsOn()).thenReturn(Collections.singletonList("dependency-node"));

        List<ExecutionNode> nodes = Arrays.asList(infraNode, valNode, depNode, credNode);
        ExecutionGraph graph = graphBuilder.build(nodes);

        List<String> order = graph.getTopologicalOrder();
        assertTrue(order.indexOf("dependency-validation-node") < order.indexOf("dependency-node"), "dependency-validation-node MUST execute BEFORE dependency-node for EXISTING_EXTERNAL");
        assertTrue(order.indexOf("dependency-node") < order.indexOf("credential-node"));
    }
}
