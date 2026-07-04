package com.autopilot.service.deployment.runtime.configuration;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class ConfigurationContract {
    private String configurationStyle; // e.g. SPRING_DATASOURCE, NEXT_JS_ENV, EXPRESS_DOTENV
    private Set<String> requiredVariables;
    private Set<String> optionalVariables;
    private Map<String, String> resolvedVariables;
    private Set<String> conflictingVariables;
    private Set<String> missingVariables;
    
    private String provider;
    private int confidence;
    private List<String> sourceFiles;
    private String provenance;
}
