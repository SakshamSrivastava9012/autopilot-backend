package com.autopilot.service;

import com.autopilot.entity.Deployment;
import com.autopilot.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortAllocatorService {

    private final DeploymentRepository deploymentRepository;

    private static final int BASE_PORT = 3001;

    public synchronized int allocatePort() {
        return allocatePort(Collections.emptyList());
    }

    public synchronized int allocatePort(Collection<Integer> excludedPorts) {
        int maxPort = BASE_PORT - 1;
        boolean found = false;

        List<Deployment> allDeployments = deploymentRepository.findAll();
        for (Deployment d : allDeployments) {
            if (d == null || "DESTROYED".equalsIgnoreCase(d.getStatus())) {
                continue;
            }

            if (d.getAssignedPort() != null) {
                maxPort = Math.max(maxPort, d.getAssignedPort());
                found = true;
            }

            if (d.getDeployedServicesJson() != null && !d.getDeployedServicesJson().isBlank()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    String json = d.getDeployedServicesJson().trim();
                    List<com.autopilot.dto.DeployedService> svcs = null;
                    if (json.startsWith("{")) {
                        com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(json);
                        if (rootNode.has("deployedServices")) {
                            svcs = mapper.convertValue(rootNode.get("deployedServices"),
                                    new com.fasterxml.jackson.core.type.TypeReference<List<com.autopilot.dto.DeployedService>>() {});
                        }
                    } else {
                        svcs = mapper.readValue(json,
                                new com.fasterxml.jackson.core.type.TypeReference<List<com.autopilot.dto.DeployedService>>() {});
                    }

                    if (svcs != null) {
                        for (com.autopilot.dto.DeployedService svc : svcs) {
                            if (svc.getHostPort() > 0) {
                                maxPort = Math.max(maxPort, svc.getHostPort());
                                found = true;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (excludedPorts != null) {
            for (Integer p : excludedPorts) {
                if (p != null) {
                    maxPort = Math.max(maxPort, p);
                    found = true;
                }
            }
        }

        if (!found) {
            return BASE_PORT;
        }
        return maxPort + 1;
    }
}