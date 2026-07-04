package com.autopilot.service.deployment.v5.runtime.execution.diagnostics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageOptimizationReport {
    private String imageName;
    private long currentSizeBytes;
    private long recommendedSizeBytes;
    private long potentialSavingsBytes;
    private double savingsPercentage;
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();
}
