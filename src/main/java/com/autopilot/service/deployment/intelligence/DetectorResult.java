package com.autopilot.service.deployment.intelligence;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Encapsulates a discovery made by a RepositoryScanner.
 * Enforces provenance tracking and confidence scoring.
 */
@Data
@Builder
public class DetectorResult {
    private String category; // e.g., "CAPABILITY", "FRAMEWORK", "DATABASE", "ASSET_DIR"
    private String key;      // e.g., "REST_API", "SPRING_BOOT", "MONGODB"
    private String value;    // e.g., "true", "3.1.2", "mongodb://..."
    
    private double confidence; // 0.0 to 1.0
    private String source;     // e.g., "pom.xml" or "application.yml"
    private List<String> evidence; // e.g., ["Found Spring MVC annotations", "Found @RestController"]
}
