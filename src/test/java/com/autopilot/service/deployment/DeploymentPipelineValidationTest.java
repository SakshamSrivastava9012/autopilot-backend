package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.analyzer.model.DeploymentContext;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentPipelineValidationTest {

    @Test
    void testMissingServiceInPortMap() {
        ServiceConfig expectedSvc = new ServiceConfig();
        expectedSvc.setName("spring-boot-app");
        expectedSvc.setServiceId("svc-123");

        ServiceConfig otherSvc = new ServiceConfig();
        otherSvc.setName("frontend");
        otherSvc.setServiceId("svc-456");

        Map<ServiceConfig, Integer> portMap = new HashMap<>();
        portMap.put(otherSvc, 3121);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            DeploymentPipelineService.requireHostPort(portMap, expectedSvc, expectedSvc.getName());
        });

        String msg = ex.getMessage();
        assertTrue(msg.contains("Missing backend container port."));
        assertTrue(msg.contains("Expected service:"));
        assertTrue(msg.contains("spring-boot-app"));
        assertTrue(msg.contains("Known services:"));
        assertTrue(msg.contains("frontend"));
        assertTrue(msg.contains("Port map:"));
        assertTrue(msg.contains("frontend=3121"));
    }

    @Test
    void testMissingBackendPort() {
        ServiceConfig backendSvc = new ServiceConfig();
        backendSvc.setName("backend-api");
        backendSvc.setServiceId("backend-id");

        Map<ServiceConfig, Integer> portMap = new HashMap<>();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            DeploymentPipelineService.requireHostPort(portMap, backendSvc, backendSvc.getName());
        });
        assertTrue(ex.getMessage().contains("Missing backend container port."));
    }

    @Test
    void testMissingFrontendPort() {
        ServiceConfig frontendSvc = new ServiceConfig();
        frontendSvc.setName("frontend-app");
        frontendSvc.setServiceId("frontend-id");

        Map<ServiceConfig, Integer> portMap = new HashMap<>();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            DeploymentPipelineService.requireHostPort(portMap, frontendSvc, frontendSvc.getName());
        });
        assertTrue(ex.getMessage().contains("Missing backend container port."));
    }

    @Test
    void testMissingHealthCheckPortAndMetadata() {
        ServiceConfig svc = new ServiceConfig();
        // Missing name
        assertThrows(NullPointerException.class, svc::validate);

        svc.setName("my-service");
        // Missing serviceId
        assertThrows(NullPointerException.class, svc::validate);

        svc.setServiceId("id-123");
        // Missing path
        assertThrows(NullPointerException.class, svc::validate);

        svc.setPath("/some/path");
        // Missing framework
        assertThrows(NullPointerException.class, svc::validate);

        svc.setFramework("react");
        // Valid now
        assertDoesNotThrow(svc::validate);
    }

    @Test
    void testFrameworkMetadataValidation() {
        FrameworkMetadata metadata = FrameworkMetadata.builder().build();
        assertThrows(NullPointerException.class, metadata::validate);
    }

    @Test
    void testNullDeploymentConfiguration() {
        DeploymentContext context = DeploymentContext.builder().build();
        assertThrows(NullPointerException.class, context::validate);
    }
}
