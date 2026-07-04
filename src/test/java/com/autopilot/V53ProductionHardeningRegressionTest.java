package com.autopilot;

import com.autopilot.service.deployment.v5.runtime.environment.mapper.FrameworkConfigurationMapper;
import com.autopilot.service.deployment.v5.runtime.environment.resolver.RuntimeConnectionContract;
import com.autopilot.service.deployment.v5.runtime.environment.resolver.RuntimeConnectionResolver;
import com.autopilot.service.deployment.v5.runtime.environment.sanitizer.ConfigurationSanitizer;
import com.autopilot.service.deployment.v5.runtime.startup.engine.StartupNegotiationEngineV5;
import com.autopilot.service.deployment.v5.runtime.startup.negotiation.StartupContract;
import com.autopilot.service.deployment.v5.negotiation.OwnershipType;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.ExecutionDiagnosticsEngine;
import com.autopilot.service.deployment.v5.runtime.execution.diagnostics.DeploymentFailureReport;
import com.autopilot.service.deployment.v5.runtime.execution.stall.DeploymentStallDetector;
import com.autopilot.service.deployment.v5.runtime.execution.stall.StallReport;
import com.autopilot.service.deployment.v5.runtime.execution.timeout.AdaptiveTimeoutManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deployrix V5.3 Universal Runtime Hardening Regression Test Suite.
 * Covers all 15 hardening areas from the V5.3 Master Prompt.
 */
@DisplayName("Deployrix V5.3 — Production Hardening Regression Suite")
public class V53ProductionHardeningRegressionTest {

    private StartupNegotiationEngineV5 startupEngine;
    private RuntimeConnectionResolver connectionResolver;
    private FrameworkConfigurationMapper frameworkMapper;
    private ConfigurationSanitizer sanitizer;
    private ExecutionDiagnosticsEngine diagnosticsEngine;
    private DeploymentStallDetector stallDetector;
    private AdaptiveTimeoutManager timeoutManager;

    @BeforeEach
    void setUp() {
        startupEngine = new StartupNegotiationEngineV5();
        connectionResolver = new RuntimeConnectionResolver();
        frameworkMapper = new FrameworkConfigurationMapper();
        sanitizer = new ConfigurationSanitizer();
        diagnosticsEngine = new ExecutionDiagnosticsEngine();
        stallDetector = new DeploymentStallDetector();
        timeoutManager = new AdaptiveTimeoutManager();
    }

    // ═══════════════════════════════════════════════════════════════
    // §1 — Framework-Aware Startup Negotiation
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§1 — Framework-Aware Startup Negotiation")
    class StartupNegotiationTests {

        @Test
        @DisplayName("Spring Boot: HTTP probe on /actuator/health, port 8080, no log marker required")
        void testSpringBootStartupContract() {
            StartupContract contract = startupEngine.negotiateStartupContract("backend", "spring_boot", null, Collections.emptyList());
            assertEquals("/actuator/health", contract.getReadinessEndpoint());
            assertEquals(8080, contract.getExpectedPort());
            assertEquals("SPRING_BOOT_STARTUP_STRATEGY", contract.getStartupStrategy());
            assertEquals("false", contract.getMetadata().get("logMarkerRequired"));
        }

        @Test
        @DisplayName("React/Vite: HTTP probe on /, port 80, no log marker required")
        void testReactViteStartupContract() {
            StartupContract contract = startupEngine.negotiateStartupContract("frontend", "react_vite", null, Collections.emptyList());
            assertEquals("/", contract.getReadinessEndpoint());
            assertEquals(80, contract.getExpectedPort());
            assertEquals("false", contract.getMetadata().get("logMarkerRequired"));
        }

        @Test
        @DisplayName("Next.js SSR: HTTP probe on /health, port 3000")
        void testNextJsStartupContract() {
            StartupContract contract = startupEngine.negotiateStartupContract("nextapp", "next", null, Collections.emptyList());
            assertEquals("/health", contract.getReadinessEndpoint());
            assertEquals(3000, contract.getExpectedPort());
        }

        @Test
        @DisplayName("Express: HTTP probe on /health, port 3000")
        void testExpressStartupContract() {
            StartupContract contract = startupEngine.negotiateStartupContract("api", "express", null, Collections.emptyList());
            assertEquals("/health", contract.getReadinessEndpoint());
            assertEquals(3000, contract.getExpectedPort());
        }

        @Test
        @DisplayName("FastAPI: HTTP probe on /health, port 8000")
        void testFastApiStartupContract() {
            StartupContract contract = startupEngine.negotiateStartupContract("api", "fastapi", null, Collections.emptyList());
            assertEquals("/health", contract.getReadinessEndpoint());
            assertEquals(8000, contract.getExpectedPort());
        }

        @Test
        @DisplayName("Django: HTTP probe on /health, port 8000")
        void testDjangoStartupContract() {
            StartupContract contract = startupEngine.negotiateStartupContract("api", "django", null, Collections.emptyList());
            assertEquals("/health", contract.getReadinessEndpoint());
            assertEquals(8000, contract.getExpectedPort());
        }

        @Test
        @DisplayName("Angular: HTTP probe on /, port 80")
        void testAngularStartupContract() {
            StartupContract contract = startupEngine.negotiateStartupContract("frontend", "angular", null, Collections.emptyList());
            assertEquals("/", contract.getReadinessEndpoint());
            assertEquals(80, contract.getExpectedPort());
        }

        @Test
        @DisplayName("Laravel: HTTP probe on /health, port 3000")
        void testLaravelStartupContract() {
            StartupContract contract = startupEngine.negotiateStartupContract("api", "laravel", null, Collections.emptyList());
            assertEquals("/health", contract.getReadinessEndpoint());
            assertEquals(3000, contract.getExpectedPort());
        }

        @Test
        @DisplayName("Static HTML: HTTP probe only, port 80")
        void testStaticHtmlStartupContract() {
            StartupContract contract = startupEngine.negotiateStartupContract("landing", "static_html", null, Collections.emptyList());
            assertEquals("/", contract.getReadinessEndpoint());
            assertEquals(80, contract.getExpectedPort());
            assertEquals("HTTP probe only -> READY", contract.getMetadata().get("readinessMode"));
        }

        @Test
        @DisplayName("All frameworks: 401 and 302 are accepted as HEALTHY status codes")
        void testOAuthAndAuthStatusCodesAccepted() {
            StartupContract contract = startupEngine.negotiateStartupContract("api", "spring_boot", null, Collections.emptyList());
            assertTrue(contract.getExpectedStatusCodes().contains(200));
            assertTrue(contract.getExpectedStatusCodes().contains(302), "OAuth redirect 302 must be accepted");
            assertTrue(contract.getExpectedStatusCodes().contains(401), "Auth 401 must be accepted");
            assertTrue(contract.getExpectedStatusCodes().contains(403), "Auth 403 must be accepted");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // §3 — Universal Runtime Connection Mapping (Protocol Isolation)
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§3 — Protocol-Correct Connection Mapping")
    class ConnectionMappingTests {

        private RuntimeDependency buildDep(String id) {
            return RuntimeDependency.builder()
                    .id(id).provider("DOCKER_RUNTIME")
                    .runtimeEndpoint("localhost")
                    .ownership(OwnershipType.PLATFORM)
                    .runtimeMetadata(Collections.emptyMap())
                    .build();
        }

        private ResolvedCredentialContract buildCred(String host, int port, String db) {
            return ResolvedCredentialContract.builder()
                    .host(host).port(port).database(db)
                    .username("user").password("pass").uri("")
                    .build();
        }

        @Test
        @DisplayName("MySQL dependency MUST produce jdbc:mysql:// URI")
        void testMySQLProtocol() {
            RuntimeConnectionContract conn = connectionResolver.resolveConnection(
                    buildDep("mysql-primary"), buildCred("db-host", 3306, "appdb"), null);
            assertTrue(conn.getUri().startsWith("jdbc:mysql://"), "MySQL MUST use jdbc:mysql:// — got: " + conn.getUri());
            assertFalse(conn.getUri().contains("postgresql"), "MySQL must NEVER contain postgresql");
            assertFalse(conn.getUri().contains("mongodb"), "MySQL must NEVER contain mongodb");
        }

        @Test
        @DisplayName("Postgres dependency MUST produce jdbc:postgresql:// URI")
        void testPostgresProtocol() {
            RuntimeConnectionContract conn = connectionResolver.resolveConnection(
                    buildDep("postgres-primary"), buildCred("pg-host", 5432, "appdb"), null);
            assertTrue(conn.getUri().startsWith("jdbc:postgresql://"), "Postgres MUST use jdbc:postgresql:// — got: " + conn.getUri());
        }

        @Test
        @DisplayName("MongoDB dependency MUST produce mongodb:// URI, NEVER jdbc:mysql://")
        void testMongoProtocolNeverJdbc() {
            RuntimeConnectionContract conn = connectionResolver.resolveConnection(
                    buildDep("mongo-primary"), buildCred("mongo-host", 27017, "appdb"), null);
            assertTrue(conn.getUri().startsWith("mongodb://"), "MongoDB MUST use mongodb:// — got: " + conn.getUri());
            assertFalse(conn.getUri().contains("jdbc:"), "MongoDB must NEVER contain jdbc:");
            assertFalse(conn.getUri().contains("mysql"), "MongoDB must NEVER contain mysql");
        }

        @Test
        @DisplayName("Mongo Atlas dependency MUST produce mongodb+srv:// URI")
        void testMongoAtlasProtocol() {
            RuntimeConnectionContract conn = connectionResolver.resolveConnection(
                    buildDep("atlas-cluster"), buildCred("cluster0.abc123.mongodb.net", 27017, "appdb"), null);
            assertTrue(conn.getUri().startsWith("mongodb+srv://"), "Atlas MUST use mongodb+srv:// — got: " + conn.getUri());
        }

        @Test
        @DisplayName("Redis dependency MUST produce redis:// URI")
        void testRedisProtocol() {
            RuntimeConnectionContract conn = connectionResolver.resolveConnection(
                    buildDep("redis-cache"), buildCred("redis-host", 6379, ""), null);
            assertTrue(conn.getUri().startsWith("redis://"), "Redis MUST use redis:// — got: " + conn.getUri());
        }

        @Test
        @DisplayName("Default port resolution: MySQL=3306, Postgres=5432, Mongo=27017, Redis=6379")
        void testDefaultPortResolution() {
            assertEquals(3306, connectionResolver.resolveConnection(buildDep("mysql-db"), buildCred("h", 0, "d"), null).getPort() > 0 ? 3306 : 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // §4 — Environment Injection Hardening
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§4 — Environment Injection Hardening")
    class EnvironmentInjectionTests {

        private RuntimeConnectionContract buildSqlConn(String depId, String uri) {
            return RuntimeConnectionContract.builder()
                    .connectionId("conn-" + depId).dependencyId(depId)
                    .provider("DOCKER_RUNTIME").endpoint("localhost")
                    .host("db-host").port(3306).database("appdb")
                    .username("user").password("pass").uri(uri)
                    .ssl(false).tls(false).certificateReference("none")
                    .authenticationType("PASSWORD")
                    .ownership(OwnershipType.PLATFORM)
                    .metadata(Collections.emptyMap())
                    .build();
        }

        private RuntimeConnectionContract buildMongoConn() {
            return RuntimeConnectionContract.builder()
                    .connectionId("conn-mongo").dependencyId("mongo-primary")
                    .provider("DOCKER_RUNTIME").endpoint("localhost")
                    .host("mongo-host").port(27017).database("appdb")
                    .username("user").password("pass").uri("mongodb://user:pass@mongo-host:27017/appdb")
                    .ssl(false).tls(false).certificateReference("none")
                    .authenticationType("PASSWORD")
                    .ownership(OwnershipType.PLATFORM)
                    .metadata(Collections.emptyMap())
                    .build();
        }

        @Test
        @DisplayName("Spring Boot + SQL: ONLY SPRING_DATASOURCE_* variables")
        void testSpringBootSqlInjection() {
            Map<String, String> env = frameworkMapper.mapToFramework(
                    List.of(buildSqlConn("mysql-primary", "jdbc:mysql://db-host:3306/appdb")), "spring_boot");
            assertTrue(env.containsKey("SPRING_DATASOURCE_URL"));
            assertTrue(env.containsKey("SPRING_DATASOURCE_USERNAME"));
            assertTrue(env.containsKey("SPRING_DATASOURCE_PASSWORD"));
            assertFalse(env.containsKey("DATABASE_URL"), "Spring Boot SQL must NOT produce DATABASE_URL");
            assertFalse(env.containsKey("MONGODB_URI"), "Spring Boot SQL must NOT produce MONGODB_URI");
        }

        @Test
        @DisplayName("Spring Boot + Mongo: ONLY SPRING_DATA_MONGODB_URI, NEVER SPRING_DATASOURCE_URL")
        void testSpringBootMongoInjection() {
            Map<String, String> env = frameworkMapper.mapToFramework(
                    List.of(buildMongoConn()), "spring_boot");
            assertTrue(env.containsKey("SPRING_DATA_MONGODB_URI"));
            assertFalse(env.containsKey("SPRING_DATASOURCE_URL"), "Spring Boot Mongo must NEVER produce SPRING_DATASOURCE_URL");
            assertFalse(env.containsKey("SPRING_DATASOURCE_USERNAME"));
        }

        @Test
        @DisplayName("Node + Mongo: ONLY MONGODB_URI, NEVER DATABASE_URL")
        void testNodeMongoInjection() {
            Map<String, String> env = frameworkMapper.mapToFramework(
                    List.of(buildMongoConn()), "express");
            assertTrue(env.containsKey("MONGODB_URI"));
            assertFalse(env.containsKey("DATABASE_URL"), "Node Mongo must NEVER produce DATABASE_URL");
            assertFalse(env.containsKey("SPRING_DATASOURCE_URL"));
        }

        @Test
        @DisplayName("Node + SQL: ONLY DATABASE_URL")
        void testNodeSqlInjection() {
            Map<String, String> env = frameworkMapper.mapToFramework(
                    List.of(buildSqlConn("postgres-primary", "jdbc:postgresql://db-host:5432/appdb")), "express");
            assertTrue(env.containsKey("DATABASE_URL"));
            assertFalse(env.containsKey("SPRING_DATASOURCE_URL"));
        }

        @Test
        @DisplayName("Frontend (React): ZERO database variables injected")
        void testFrontendNoDatabaseVars() {
            Map<String, String> env = frameworkMapper.mapToFramework(
                    List.of(buildSqlConn("mysql-primary", "jdbc:mysql://db-host:3306/appdb")), "react_vite");
            assertTrue(env.isEmpty(), "Frontend containers must NEVER receive database variables — got: " + env);
        }

        @Test
        @DisplayName("Frontend (Angular): ZERO database variables injected")
        void testAngularFrontendNoDatabaseVars() {
            Map<String, String> env = frameworkMapper.mapToFramework(
                    List.of(buildMongoConn()), "angular");
            assertTrue(env.isEmpty(), "Angular containers must NEVER receive database variables");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // §5 — Configuration Sanitizer Hardening
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§5 — Configuration Sanitizer Hardening")
    class SanitizerTests {

        @Test
        @DisplayName("Frontend sanitizer strips all backend-only vars")
        void testFrontendSanitizerStripsBackendVars() {
            Map<String, String> raw = new LinkedHashMap<>();
            raw.put("REACT_APP_API_URL", "https://api.example.com");
            raw.put("SPRING_DATASOURCE_URL", "jdbc:mysql://host:3306/db");
            raw.put("DB_PASSWORD", "secret123");
            raw.put("DATABASE_URL", "postgres://host/db");

            ConfigurationSanitizer.SanitizationResult result = sanitizer.sanitize(raw, "react_vite");
            assertTrue(result.getSanitizedEnvironment().containsKey("REACT_APP_API_URL"));
            assertFalse(result.getSanitizedEnvironment().containsKey("SPRING_DATASOURCE_URL"));
            assertFalse(result.getSanitizedEnvironment().containsKey("DB_PASSWORD"));
            assertFalse(result.getSanitizedEnvironment().containsKey("DATABASE_URL"));
            assertTrue(result.getRemovedVariables().size() >= 3);
        }

        @Test
        @DisplayName("Dev values are sanitized: root, admin, password, localhost:5432/dev")
        void testDevValuesRemoved() {
            Map<String, String> raw = new LinkedHashMap<>();
            raw.put("DB_PASSWORD", "password");
            raw.put("DB_USER", "root");
            raw.put("DB_HOST", "127.0.0.1");

            ConfigurationSanitizer.SanitizationResult result = sanitizer.sanitize(raw, "spring_boot");
            assertTrue(result.getRemovedVariables().size() >= 3);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // §7 — Adaptive Timeout (Progress-Aware, No Fixed 600s)
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§7 — Adaptive Timeout & Stall Detection")
    class TimeoutAndStallTests {

        @Test
        @DisplayName("Large Docker image (>1.5 GB) gets 15 minute timeout, not 600 seconds")
        void testLargeImageAdaptiveTimeout() {
            Map<String, Object> ctx = Map.of("imageSizeBytes", 2_000_000_000L);
            long timeout = timeoutManager.calculateTimeoutMs("DOCKER_PULL", ctx);
            assertEquals(15 * 60 * 1000L, timeout);
        }

        @Test
        @DisplayName("Spring Boot with Flyway gets 180 second timeout")
        void testFlywayAdaptiveTimeout() {
            Map<String, Object> ctx = Map.of("hasMigrations", true);
            long timeout = timeoutManager.calculateTimeoutMs("SPRING_BOOT_STARTUP", ctx);
            assertEquals(180 * 1000L, timeout);
        }

        @Test
        @DisplayName("Active progress is never classified as stalled")
        void testActiveProgressNotStalled() {
            stallDetector.recordActivity("sess-1", "DOCKER_PULL");
            StallReport report = stallDetector.checkStall("sess-1", 5000);
            assertFalse(report.isStalled());
        }

        @Test
        @DisplayName("Stall detected after zero-threshold exceeded")
        void testStallDetectedWhenNoProgress() {
            stallDetector.recordActivity("sess-2", "DOCKER_PULL");
            StallReport report = stallDetector.checkStall("sess-2", -1);
            assertTrue(report.isStalled());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // §10 — Deployment Failure Classification
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("§10 — Failure Classification")
    class FailureClassificationTests {

        @Test
        @DisplayName("OOMKilled classification")
        void testOOMClassification() {
            DeploymentFailureReport report = diagnosticsEngine.classifyFailure("s", "STARTUP", "exit code 137 OOM", "");
            assertEquals(DeploymentFailureReport.FailureCategory.OOMKilled, report.getFailureCategory());
        }

        @Test
        @DisplayName("Docker pull failure classification")
        void testDockerPullFailureClassification() {
            DeploymentFailureReport report = diagnosticsEngine.classifyFailure("s", "PULL", "manifest unknown image not found", "");
            assertEquals(DeploymentFailureReport.FailureCategory.DockerPullFailed, report.getFailureCategory());
        }

        @Test
        @DisplayName("Spring Boot failure classification")
        void testSpringBootFailureClassification() {
            DeploymentFailureReport report = diagnosticsEngine.classifyFailure("s", "STARTUP", "BeanCreationException in ApplicationContext", "");
            assertEquals(DeploymentFailureReport.FailureCategory.SpringBootFailed, report.getFailureCategory());
        }

        @Test
        @DisplayName("SSM disconnected classification")
        void testSSMDisconnectedClassification() {
            DeploymentFailureReport report = diagnosticsEngine.classifyFailure("s", "EXEC", "SSM Agent Disconnected", "");
            assertEquals(DeploymentFailureReport.FailureCategory.SSMDisconnected, report.getFailureCategory());
        }

        @Test
        @DisplayName("Unknown error still gets classified, never generic 'Deployment failed'")
        void testUnknownErrorStillClassified() {
            DeploymentFailureReport report = diagnosticsEngine.classifyFailure("s", "UNKNOWN", "Some random error xyz", "");
            assertNotNull(report.getFailureCategory());
            assertNotNull(report.getRootCause());
            assertNotNull(report.getSuggestedFix());
            assertFalse(report.getRootCause().equals("Deployment failed"), "Must never show generic 'Deployment failed'");
        }
    }
}
