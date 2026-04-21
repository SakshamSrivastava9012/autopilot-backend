package com.autopilot.controller;

import com.autopilot.entity.Deployment;
import com.autopilot.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for Config Intelligence results.
 * Exposes detected secrets, env vars, databases, and env map to the frontend.
 */
@RestController
@RequestMapping("/api/deployments/{deploymentId}/config")
@RequiredArgsConstructor
public class ConfigIntelligenceController {

    private final DeploymentRepository deploymentRepository;

    /**
     * GET /api/deployments/{id}/config
     * Returns the stored config intelligence data for a deployment.
     */
    @GetMapping
    public ResponseEntity<?> getConfigIntelligence(@PathVariable String deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId).orElse(null);
        if (deployment == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deploymentId", deploymentId);
        result.put("strategyUsed", deployment.getStrategyUsed() != null ? deployment.getStrategyUsed() : "UNKNOWN");
        result.put("buildCommand", deployment.getBuildCommand() != null ? deployment.getBuildCommand() : "");
        result.put("startCommand", deployment.getStartCommand() != null ? deployment.getStartCommand() : "");
        result.put("runtimeVersion", deployment.getRuntimeVersion() != null ? deployment.getRuntimeVersion() : "");
        result.put("detectedDatabases", deployment.getDetectedDatabases() != null ? deployment.getDetectedDatabases() : "");
        result.put("detectedCaches", deployment.getDetectedCaches() != null ? deployment.getDetectedCaches() : "");
        result.put("secretCount", deployment.getSecretCount() != null ? deployment.getSecretCount() : 0);
        result.put("envVarCount", deployment.getEnvVarCount() != null ? deployment.getEnvVarCount() : 0);
        result.put("secretsArn", deployment.getSecretsArn() != null ? deployment.getSecretsArn() : "");
        result.put("rdsEndpoint", deployment.getRdsEndpoint() != null ? deployment.getRdsEndpoint() : "");

        return ResponseEntity.ok(result);
    }
}
