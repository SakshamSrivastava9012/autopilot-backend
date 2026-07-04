package com.autopilot.service.deployment.v5.runtime.execution.diagnostics;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Docker Pull Analyzer & Image Optimization Engine.
 * Tracks image size, layers, download/extract progress, estimated completion, and generates ImageOptimizationReports.
 */
@Service
public class DockerPullAnalyzer {

    public ImageOptimizationReport analyzeImage(String imageName, long currentSizeBytes) {
        if (imageName == null) imageName = "app-image";
        
        long recommendedSizeBytes;
        List<String> recommendations = new ArrayList<>();

        if (imageName.contains("frontend") || imageName.contains("react") || imageName.contains("next")) {
            // Frontend image: current e.g. 520MB -> recommended e.g. 22MB (alpine/nginx multi-stage)
            recommendedSizeBytes = Math.min(currentSizeBytes, 35 * 1024 * 1024L);
            recommendations.add("Use multi-stage Docker build with Nginx Alpine static serving.");
            recommendations.add("Remove source files and build tools (node_modules, build caches) from final image layer.");
            recommendations.add("Use .dockerignore to exclude node_modules and local cache directories.");
        } else if (imageName.contains("spring") || imageName.contains("backend") || imageName.contains("java")) {
            // Java/Spring image: current e.g. 1.9GB -> recommended e.g. 350MB (Eclipse Temurin JRE Alpine)
            recommendedSizeBytes = Math.min(currentSizeBytes, 350 * 1024 * 1024L);
            recommendations.add("Use lightweight JRE base image (e.g., eclipse-temurin:21-jre-alpine).");
            recommendations.add("Leverage Spring Boot layered jar optimization in Dockerfile.");
            recommendations.add("Use multi-stage build: compile in Maven container, run artifact in minimal JRE.");
        } else {
            recommendedSizeBytes = (long) (currentSizeBytes * 0.3); // 70% reduction recommendation
            recommendations.add("Adopt multi-stage builds to eliminate build-time tools from runtime container.");
            recommendations.add("Use Minimal Alpine or Distroless base image.");
        }

        long savings = Math.max(0, currentSizeBytes - recommendedSizeBytes);
        double savingsPct = currentSizeBytes > 0 ? ((double) savings / currentSizeBytes) * 100.0 : 0.0;

        return ImageOptimizationReport.builder()
                .imageName(imageName)
                .currentSizeBytes(currentSizeBytes)
                .recommendedSizeBytes(recommendedSizeBytes)
                .potentialSavingsBytes(savings)
                .savingsPercentage(Math.round(savingsPct * 10.0) / 10.0)
                .recommendations(recommendations)
                .build();
    }
}
