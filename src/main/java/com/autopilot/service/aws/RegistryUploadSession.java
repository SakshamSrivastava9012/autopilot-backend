package com.autopilot.service.aws;

import lombok.Data;
import java.util.*;

@Data
public class RegistryUploadSession {
    private final String sessionId;
    private final String imageName;
    private final String registry;
    private final long startTime;
    
    private int totalLayers;
    private Set<String> completedLayers = new HashSet<>();
    private Map<String, RegistryLayerMetrics> layerMetrics = new HashMap<>();
    private int totalRetries;
    private long endTime;

    public RegistryUploadSession(String imageName, String registry) {
        this.sessionId = UUID.randomUUID().toString();
        this.imageName = imageName;
        this.registry = registry;
        this.startTime = System.currentTimeMillis();
    }
}
