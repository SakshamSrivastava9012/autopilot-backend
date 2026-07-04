package com.autopilot.service.deployment.intelligence.v5.detector;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;

/**
 * The standardized output of every V5 detector.
 * Every discovery must include evidence and confidence — nothing is inferred without proof.
 *
 * @since V5
 */
@Value
@Builder
public class DetectorResultV5 {
    String detectorName;
    String category;         // LANGUAGE, FRAMEWORK, CAPABILITY, DEPENDENCY, ASSET, ROUTE, SECRET, ENV, SERVICE
    String key;              // e.g. "REST_API", "SPRING_BOOT", "PostgreSQL"
    String value;            // e.g. "3.3.5", "/actuator/health", "true"
    double confidence;       // 0.0 to 1.0
    String provenance;       // e.g. "pom.xml:42"
    List<String> evidence;   // e.g. ["Found spring-boot-starter-web", "@RestController"]
    Map<String, String> metadata;
}
