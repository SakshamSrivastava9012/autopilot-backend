package com.autopilot.service.aws;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RegistryUploadReport {
    private String sessionId;
    private String imageName;
    private String registry;
    private int totalLayers;
    private int alreadyExistedLayers;
    private int uploadedLayers;
    private long totalDurationMs;
    private boolean success;
    private String errorMessage;
    private List<RegistryLayerMetrics> layerMetrics;
}
