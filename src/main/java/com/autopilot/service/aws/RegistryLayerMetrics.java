package com.autopilot.service.aws;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegistryLayerMetrics {
    private String digest;
    private long sizeBytes;
    private long uploadDurationMs;
    private int retries;
    private double throughputMbPerSec;
    private String status; // e.g. "EXISTS", "PUSHED", "FAILED"
}
