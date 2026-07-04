package com.autopilot.service.infrastructure;

import com.autopilot.entity.Deployment;
import com.autopilot.dto.DeploymentManifest;
import com.autopilot.dto.RouteDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NginxConfigService {

    private final UniversalNginxGenerator nginxGenerator;

    public String generateConfig(List<Deployment> deployments) {
        validateDeployments(deployments);
        return nginxGenerator.generate(deployments);
    }

    public void validateDeployments(List<Deployment> deployments) {
        java.util.Map<String, String> basePathToService = new java.util.HashMap<>();
        java.util.Map<Integer, String> portToService = new java.util.HashMap<>();
        java.util.Set<String> serviceIds = new java.util.HashSet<>();

        for (Deployment d : deployments) {
            if (d == null || "DESTROYED".equalsIgnoreCase(d.getStatus())) {
                continue;
            }

            DeploymentManifest manifest = UniversalNginxGenerator.parseDeploymentManifest(d.getDeployedServicesJson());
            if (manifest != null && manifest.getRoutes() != null) {
                for (RouteDescriptor route : manifest.getRoutes()) {
                    validateService(route.getTargetService(), route.getPath(), route.getInternalPort(), d.getId(), basePathToService, portToService, serviceIds);
                }
            } else {
                List<com.autopilot.dto.DeployedService> svcs = UniversalNginxGenerator.parseDeployedServices(d.getDeployedServicesJson());
                if (svcs != null && !svcs.isEmpty()) {
                    for (com.autopilot.dto.DeployedService svc : svcs) {
                        validateService(svc.getName(), svc.getBasePath(), svc.getHostPort(), d.getId(), basePathToService, portToService, serviceIds);
                    }
                } else if (d.getAssignedPort() != null && d.getAssignedPort() > 0 && d.getBasePath() != null) {
                    validateService(d.getId(), d.getBasePath(), d.getAssignedPort(), d.getId(), basePathToService, portToService, serviceIds);
                }
            }
        }
    }

    private void validateService(
            String serviceId,
            String basePath,
            int port,
            String deploymentId,
            java.util.Map<String, String> basePathToService,
            java.util.Map<Integer, String> portToService,
            java.util.Set<String> serviceIds
    ) {
        if (serviceId == null || basePath == null || basePath.isBlank() || port <= 0) {
            return;
        }

        String normPath = basePath.trim();
        if (!normPath.startsWith("/")) normPath = "/" + normPath;
        if (normPath.endsWith("/")) normPath = normPath.substring(0, normPath.length() - 1);

        if ("/health".equals(normPath)) {
            triggerDuplicateError("Base Path (Reserved for System Health Checks)", normPath,
                    "Service " + serviceId + " in deployment " + deploymentId + " attempted to reserve '/health'.");
        }

        String globalServiceId = deploymentId + ":" + serviceId;
        if (serviceIds.contains(globalServiceId)) {
            triggerDuplicateError("Service ID", serviceId,
                    "Service " + serviceId + " in deployment " + deploymentId + " is duplicate.");
        }
        serviceIds.add(globalServiceId);

        if (basePathToService.containsKey(normPath)) {
            triggerDuplicateError("Base Path (Location)", normPath,
                    "Service " + serviceId + " conflicts with Service " + basePathToService.get(normPath));
        }
        basePathToService.put(normPath, serviceId);

        if (portToService.containsKey(port)) {
            triggerDuplicateError("Port (Upstream)", String.valueOf(port),
                    "Service " + serviceId + " conflicts with Service " + portToService.get(port));
        }
        portToService.put(port, serviceId);
    }

    private void triggerDuplicateError(String entityType, String entityValue, String conflictDetail) {
        StringBuilder sb = new StringBuilder();
        sb.append("Architectural Route Generation Violation Detected!\n");
        sb.append("Conflicting Services: ").append(conflictDetail).append("\n");
        sb.append("Conflict Type: Duplicate ").append(entityType).append("\n");
        sb.append("Value: ").append(entityValue).append("\n");
        sb.append("Source Class: com.autopilot.service.infrastructure.NginxConfigService\n");
        sb.append("Source Method: generateConfig\n");
        sb.append("Call Stack:\n");
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            sb.append("  at ").append(ste.toString()).append("\n");
        }
        throw new RuntimeException(sb.toString());
    }
}