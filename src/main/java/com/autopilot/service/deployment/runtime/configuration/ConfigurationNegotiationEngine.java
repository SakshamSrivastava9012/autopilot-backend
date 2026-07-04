package com.autopilot.service.deployment.runtime.configuration;

import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class ConfigurationNegotiationEngine {

    private final ConfigurationStyleDetector styleDetector;
    private final ConfigurationResolver resolver;
    private final ConfigurationConflictAnalyzer conflictAnalyzer;
    private final ConfigurationValidator validator;
    private final EnvironmentInjector injector;

    public ConfigurationNegotiationEngine(
            ConfigurationStyleDetector styleDetector,
            ConfigurationResolver resolver,
            ConfigurationConflictAnalyzer conflictAnalyzer,
            ConfigurationValidator validator,
            EnvironmentInjector injector) {
        this.styleDetector = styleDetector;
        this.resolver = resolver;
        this.conflictAnalyzer = conflictAnalyzer;
        this.validator = validator;
        this.injector = injector;
    }

    public ConfigurationReports.ConfigurationNegotiationReport negotiateEnvironment(Map<String, String> repositoryMetadata, Map<String, String> rawEnvironmentVars) {
        System.out.println("🌐 Starting Universal Configuration Negotiation...");
        
        String style = styleDetector.detectStyle(repositoryMetadata);
        
        ConfigurationReports.ConfigurationConflictReport conflictReport = conflictAnalyzer.analyze(rawEnvironmentVars);
        if (conflictReport.isHasConflicts()) {
            System.out.println("  -> " + conflictReport.getResolutionApplied());
        }
        
        ConfigurationContract contract = resolver.resolve(style, rawEnvironmentVars);
        
        ConfigurationReports.EnvironmentValidationReport validationReport = validator.validate(contract);
        
        if (validationReport.isValid()) {
            injector.inject(contract);
            
            // Clean up rawEnvironmentVars based on negotiated style to prevent duplicates/collisions
            if ("SPRING_DATASOURCE".equals(style)) {
                rawEnvironmentVars.remove("DATABASE_URL");
                rawEnvironmentVars.remove("DB_HOST");
                rawEnvironmentVars.remove("DB_PORT");
                rawEnvironmentVars.remove("DB_NAME");
                rawEnvironmentVars.remove("DB_USER");
                rawEnvironmentVars.remove("DB_PASSWORD");
                rawEnvironmentVars.remove("REDIS_HOST");
            } else {
                rawEnvironmentVars.remove("SPRING_DATASOURCE_URL");
                rawEnvironmentVars.remove("SPRING_DATASOURCE_USERNAME");
                rawEnvironmentVars.remove("SPRING_DATASOURCE_PASSWORD");
                rawEnvironmentVars.remove("SPRING_REDIS_HOST");
            }
        }
        
        return ConfigurationReports.ConfigurationNegotiationReport.builder()
                .negotiatedStyle(style)
                .confidence(95)
                .evidenceSources(styleDetector.getEvidenceSources())
                .rationale("Negotiated based on metadata detection and conflict resolution.")
                .build();
    }
}
