package com.autopilot;

import com.autopilot.dto.DeployedService;
import com.autopilot.entity.Deployment;
import com.autopilot.enums.DeploymentStatus;
import com.autopilot.service.infrastructure.NginxConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RouteGenerationRegressionTest {

    private NginxConfigService nginxConfigService;
    private ObjectMapper mapper;

    @BeforeEach
    public void setUp() {
        nginxConfigService = new NginxConfigService(new com.autopilot.service.infrastructure.UniversalNginxGenerator());
        mapper = new ObjectMapper();
    }

    /**
     * Test Case 1: Backend only deployment.
     */
    @Test
    public void testBackendOnly() throws Exception {
        Deployment d = new Deployment();
        d.setId(UUID.randomUUID().toString());
        d.setStatus(DeploymentStatus.RUNNING.name());
        d.setAssignedPort(3101);
        d.setBasePath("/app-backend-only");

        List<DeployedService> svcs = List.of(
                new DeployedService("backend-svc", "springboot", "java", "/path", 8080, 3101, "/app-backend-only", "img", "backend", "build", "start", "21")
        );
        d.setDeployedServicesJson(mapper.writeValueAsString(svcs));

        String config = nginxConfigService.generateConfig(List.of(d));
        assertNotNull(config);
        assertTrue(config.contains("location /app-backend-only/ {"));
        assertTrue(config.contains("proxy_pass http://127.0.0.1:3101/;"));
        // Assert no duplicate locations
        int count = countOccurrences(config, "location /app-backend-only/");
        assertEquals(1, count);
    }

    /**
     * Test Case 2: Frontend only deployment.
     */
    @Test
    public void testFrontendOnly() throws Exception {
        Deployment d = new Deployment();
        d.setId(UUID.randomUUID().toString());
        d.setStatus(DeploymentStatus.RUNNING.name());
        d.setAssignedPort(3102);
        d.setBasePath("/app-frontend-only");

        List<DeployedService> svcs = List.of(
                new DeployedService("frontend-svc", "react", "js", "/path", 3000, 3102, "/app-frontend-only", "img", "frontend", "build", "start", "20")
        );
        d.setDeployedServicesJson(mapper.writeValueAsString(svcs));

        String config = nginxConfigService.generateConfig(List.of(d));
        assertNotNull(config);
        assertTrue(config.contains("location /app-frontend-only/ {"));
        assertTrue(config.contains("proxy_pass http://127.0.0.1:3102/;"));
        assertEquals(1, countOccurrences(config, "location /app-frontend-only/"));
    }

    /**
     * Test Case 3: Monorepo (Spring Boot + React).
     */
    @Test
    public void testMonorepo() throws Exception {
        Deployment d = new Deployment();
        d.setId(UUID.randomUUID().toString());
        d.setStatus(DeploymentStatus.RUNNING.name());
        d.setAssignedPort(3103);
        d.setBasePath("/app-monorepo");

        List<DeployedService> svcs = List.of(
                new DeployedService("frontend", "react", "js", "/path/frontend", 3000, 3103, "/app-monorepo", "img-fe", "frontend", "build", "start", "20"),
                new DeployedService("backend", "spring-boot", "java", "/path/backend", 8080, 3104, "/app-monorepo-api", "img-be", "backend", "build", "start", "21")
        );
        d.setDeployedServicesJson(mapper.writeValueAsString(svcs));

        String config = nginxConfigService.generateConfig(List.of(d));
        assertNotNull(config);
        assertTrue(config.contains("location /app-monorepo/ {"));
        assertTrue(config.contains("location /app-monorepo-api/ {"));
        assertEquals(1, countOccurrences(config, "location /app-monorepo/"));
        assertEquals(1, countOccurrences(config, "location /app-monorepo-api/"));
    }

    /**
     * Test Case 4: Polyrepo (Frontend + Backend).
     */
    @Test
    public void testPolyrepo() throws Exception {
        // Deployment A: Frontend
        Deployment d1 = new Deployment();
        d1.setId("polyrepo-fe");
        d1.setStatus(DeploymentStatus.RUNNING.name());
        d1.setAssignedPort(3105);
        d1.setBasePath("/app-polyrepo");
        List<DeployedService> svcs1 = List.of(
                new DeployedService("poly-fe", "react", "js", "/path", 3000, 3105, "/app-polyrepo", "img1", "frontend", "build", "start", "20")
        );
        d1.setDeployedServicesJson(mapper.writeValueAsString(svcs1));

        // Deployment B: Backend
        Deployment d2 = new Deployment();
        d2.setId("polyrepo-be");
        d2.setStatus(DeploymentStatus.RUNNING.name());
        d2.setAssignedPort(3106);
        d2.setBasePath("/app-polyrepo-api");
        List<DeployedService> svcs2 = List.of(
                new DeployedService("poly-be", "spring-boot", "java", "/path", 8080, 3106, "/app-polyrepo-api", "img2", "backend", "build", "start", "21")
        );
        d2.setDeployedServicesJson(mapper.writeValueAsString(svcs2));

        String config = nginxConfigService.generateConfig(List.of(d1, d2));
        assertNotNull(config);
        assertTrue(config.contains("location /app-polyrepo/ {"));
        assertTrue(config.contains("location /app-polyrepo-api/ {"));
        assertEquals(1, countOccurrences(config, "location /app-polyrepo/"));
        assertEquals(1, countOccurrences(config, "location /app-polyrepo-api/"));
    }

    /**
     * Test Case 5: Multiple backend services.
     */
    @Test
    public void testMultipleBackends() throws Exception {
        Deployment d = new Deployment();
        d.setId(UUID.randomUUID().toString());
        d.setStatus(DeploymentStatus.RUNNING.name());
        d.setAssignedPort(3107);
        d.setBasePath("/app-multi-backend");

        List<DeployedService> svcs = List.of(
                new DeployedService("frontend", "react", "js", "/path", 3000, 3107, "/app-multi-backend", "img-fe", "frontend", "build", "start", "20"),
                new DeployedService("backend1", "spring-boot", "java", "/path1", 8080, 3108, "/app-multi-backend-api", "img-be1", "backend", "build", "start", "21"),
                new DeployedService("backend2", "go", "go", "/path2", 8081, 3109, "/app-multi-backend-svc1", "img-be2", "backend", "build", "start", "1.21")
        );
        d.setDeployedServicesJson(mapper.writeValueAsString(svcs));

        String config = nginxConfigService.generateConfig(List.of(d));
        assertNotNull(config);
        assertTrue(config.contains("location /app-multi-backend/ {"));
        assertTrue(config.contains("location /app-multi-backend-api/ {"));
        assertTrue(config.contains("location /app-multi-backend-svc1/ {"));
        assertEquals(1, countOccurrences(config, "location /app-multi-backend/"));
        assertEquals(1, countOccurrences(config, "location /app-multi-backend-api/"));
        assertEquals(1, countOccurrences(config, "location /app-multi-backend-svc1/"));
    }

    /**
     * Test Case 6: Multiple frontend services.
     */
    @Test
    public void testMultipleFrontends() throws Exception {
        Deployment d = new Deployment();
        d.setId(UUID.randomUUID().toString());
        d.setStatus(DeploymentStatus.RUNNING.name());
        d.setAssignedPort(3110);
        d.setBasePath("/app-multi-frontend");

        List<DeployedService> svcs = List.of(
                new DeployedService("frontend1", "react", "js", "/fe1", 3000, 3110, "/app-multi-frontend", "img-fe1", "frontend", "build", "start", "20"),
                new DeployedService("frontend2", "nextjs", "js", "/fe2", 3000, 3111, "/app-multi-frontend-svc0", "img-fe2", "frontend", "build", "start", "20"),
                new DeployedService("backend", "spring-boot", "java", "/be", 8080, 3112, "/app-multi-frontend-api", "img-be", "backend", "build", "start", "21")
        );
        d.setDeployedServicesJson(mapper.writeValueAsString(svcs));

        String config = nginxConfigService.generateConfig(List.of(d));
        assertNotNull(config);
        assertTrue(config.contains("location /app-multi-frontend/ {"));
        assertTrue(config.contains("location /app-multi-frontend-svc0/ {"));
        assertTrue(config.contains("location /app-multi-frontend-api/ {"));
        assertEquals(1, countOccurrences(config, "location /app-multi-frontend/"));
        assertEquals(1, countOccurrences(config, "location /app-multi-frontend-svc0/"));
        assertEquals(1, countOccurrences(config, "location /app-multi-frontend-api/"));
    }

    /**
     * Test Case 7: Deployment retries / Repeated Nginx generation (Idempotency).
     */
    @Test
    public void testRepeatedNginxGenerationAndRetries() throws Exception {
        Deployment d = new Deployment();
        d.setId(UUID.randomUUID().toString());
        d.setStatus(DeploymentStatus.RUNNING.name());
        d.setAssignedPort(3113);
        d.setBasePath("/app-retry");

        List<DeployedService> svcs = List.of(
                new DeployedService("frontend", "react", "js", "/path", 3000, 3113, "/app-retry", "img-fe", "frontend", "build", "start", "20"),
                new DeployedService("backend", "spring-boot", "java", "/path", 8080, 3114, "/app-retry-api", "img-be", "backend", "build", "start", "21")
        );
        d.setDeployedServicesJson(mapper.writeValueAsString(svcs));

        // Generate first time
        String config1 = nginxConfigService.generateConfig(List.of(d));

        // Generate second time
        String config2 = nginxConfigService.generateConfig(List.of(d));

        assertEquals(config1, config2);
        assertEquals(1, countOccurrences(config1, "location /app-retry/"));
        assertEquals(1, countOccurrences(config1, "location /app-retry-api/"));
    }

    /**
     * Test Case 8: Repeated manifest deserialization and JSON parsing formats.
     */
    @Test
    public void testRepeatedManifestLoads() throws Exception {
        // Format A: Pure JSON list
        String jsonList = "[{\"name\":\"frontend\",\"framework\":\"react\",\"language\":\"js\",\"path\":\"/\",\"port\":3000,\"hostPort\":3115,\"basePath\":\"/app-list\",\"imageUri\":\"img\",\"role\":\"frontend\"}]";
        Deployment d1 = new Deployment();
        d1.setId("d1");
        d1.setDeployedServicesJson(jsonList);

        // Format B: Full manifest JSON object containing key "deployedServices"
        String jsonObject = "{\"deployedServices\":[{\"name\":\"frontend\",\"framework\":\"react\",\"language\":\"js\",\"path\":\"/\",\"port\":3000,\"hostPort\":3116,\"basePath\":\"/app-obj\",\"imageUri\":\"img\",\"role\":\"frontend\"}],\"framework\":\"react\",\"runtime\":\"20\"}";
        Deployment d2 = new Deployment();
        d2.setId("d2");
        d2.setDeployedServicesJson(jsonObject);

        String config = nginxConfigService.generateConfig(List.of(d1, d2));
        assertNotNull(config);
        assertTrue(config.contains("location /app-list/ {"));
        assertTrue(config.contains("location /app-obj/ {"));
        assertEquals(1, countOccurrences(config, "location /app-list/"));
        assertEquals(1, countOccurrences(config, "location /app-obj/"));
    }

    /**
     * Test Case 9: Validation triggers Exception for Duplicate Locations.
     */
    @Test
    public void testValidationDuplicateLocations() throws Exception {
        // Create conflicting deployments with same base path location
        Deployment d1 = new Deployment();
        d1.setId("d1");
        d1.setStatus(DeploymentStatus.RUNNING.name());
        d1.setAssignedPort(3117);
        d1.setBasePath("/app-conflict");

        Deployment d2 = new Deployment();
        d2.setId("d2");
        d2.setStatus(DeploymentStatus.RUNNING.name());
        d2.setAssignedPort(3118);
        d2.setBasePath("/app-conflict"); // duplicate location

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            nginxConfigService.generateConfig(List.of(d1, d2));
        });

        assertTrue(ex.getMessage().contains("Architectural Route Generation Violation Detected!"));
        assertTrue(ex.getMessage().contains("Conflict Type: Duplicate Base Path (Location)"));
        assertTrue(ex.getMessage().contains("Value: /app-conflict"));
        assertTrue(ex.getMessage().contains("Source Class: com.autopilot.service.infrastructure.NginxConfigService"));
        assertTrue(ex.getMessage().contains("Source Method: generateConfig"));
        assertTrue(ex.getMessage().contains("Call Stack:"));
    }

    /**
     * Test Case 10: Validation triggers Exception for Duplicate Ports.
     */
    @Test
    public void testValidationDuplicatePorts() throws Exception {
        // Create conflicting deployments with same host port
        Deployment d1 = new Deployment();
        d1.setId("d1");
        d1.setStatus(DeploymentStatus.RUNNING.name());
        d1.setAssignedPort(3119);
        d1.setBasePath("/app-one");

        Deployment d2 = new Deployment();
        d2.setId("d2");
        d2.setStatus(DeploymentStatus.RUNNING.name());
        d2.setAssignedPort(3119); // duplicate port
        d2.setBasePath("/app-two");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            nginxConfigService.generateConfig(List.of(d1, d2));
        });

        assertTrue(ex.getMessage().contains("Conflict Type: Duplicate Port (Upstream)"));
        assertTrue(ex.getMessage().contains("Value: 3119"));
    }

    /**
     * Test Case 11: Validation triggers Exception for Reserved System Health Check Location.
     */
    @Test
    public void testValidationReservedSystemHealthCheckLocation() throws Exception {
        Deployment d = new Deployment();
        d.setId("d1");
        d.setStatus(DeploymentStatus.RUNNING.name());
        d.setAssignedPort(3120);
        d.setBasePath("/health"); // reserved by the system

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            nginxConfigService.generateConfig(List.of(d));
        });

        assertTrue(ex.getMessage().contains("Conflict Type: Duplicate Base Path (Reserved for System Health Checks)"));
        assertTrue(ex.getMessage().contains("Value: /health"));
    }

    /**
     * Test Case 12: Strict Namespace-Based routing (no file-based locations).
     */
    @Test
    public void testStrictNamespaceRouting() throws Exception {
        Deployment d = new Deployment();
        d.setId("abc123did");
        d.setStatus(DeploymentStatus.RUNNING.name());
        d.setAssignedPort(3103);
        d.setBasePath("/app-abc123did");

        List<DeployedService> svcs = List.of(
                new DeployedService("frontend", "react", "js", "/path/frontend", 3000, 3103, "/app-abc123did", "img-fe", "frontend", "build", "start", "20"),
                new DeployedService("backend", "spring-boot", "java", "/path/backend", 8080, 3104, "/app-abc123did-api", "img-be", "backend", "build", "start", "21")
        );
        d.setDeployedServicesJson(mapper.writeValueAsString(svcs));

        String config = nginxConfigService.generateConfig(List.of(d));
        assertNotNull(config);
        
        // Assert prefix locations exist
        assertTrue(config.contains("location /app-abc123did/ {"));
        assertTrue(config.contains("location /app-abc123did-api/ {"));

        // Assert absolutely no forbidden file-based location blocks are present
        assertFalse(config.contains("location /assets"));
        assertFalse(config.contains("location /index.html"));
        assertFalse(config.contains("location /favicon.ico"));
        assertFalse(config.contains("location /vite.svg"));
        assertFalse(config.contains("location /robots.txt"));
        assertFalse(config.contains("location /_next"));
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
