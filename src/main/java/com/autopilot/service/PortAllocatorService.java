package com.autopilot.service;

import com.autopilot.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortAllocatorService {

    private final DeploymentRepository deploymentRepository;

    private static final int BASE_PORT = 3001;

    public synchronized int allocatePort() {

        Integer maxPort = deploymentRepository.findMaxAssignedPort();

        if (maxPort == null) {
            return BASE_PORT;
        }

        return maxPort + 1;
    }
}