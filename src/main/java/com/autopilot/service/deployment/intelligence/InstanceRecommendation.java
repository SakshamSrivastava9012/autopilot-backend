package com.autopilot.service.deployment.intelligence;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstanceRecommendation {
    private String instanceType;
    private String estimatedRam;
    private String estimatedCpu;
    private double estimatedCost;
    private int confidenceScore;
    private String reason;
}
