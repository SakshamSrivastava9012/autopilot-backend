package com.autopilot.service.deployment.runtime.configuration;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;

@Service
public class ConfigurationConflictAnalyzer {

    public ConfigurationReports.ConfigurationConflictReport analyze(Map<String, String> collectedVars) {
        System.out.println("⚠️ Analyzing Configuration Conflicts...");
        
        boolean hasConflicts = false;
        String resolution = "No conflicts detected.";
        
        if (collectedVars.containsKey("SPRING_DATASOURCE_URL") && collectedVars.containsKey("DATABASE_URL")) {
            hasConflicts = true;
            resolution = "Resolved: Prioritizing SPRING_DATASOURCE_URL based on negotiated Spring Boot style. Dropping DATABASE_URL.";
        }
        
        return ConfigurationReports.ConfigurationConflictReport.builder()
                .hasConflicts(hasConflicts)
                .resolutionApplied(resolution)
                .build();
    }
}
