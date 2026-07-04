package com.autopilot.service.deployment.runtime.configuration;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;

@Service
public class ConfigurationResolver {

    public ConfigurationContract resolve(String style, Map<String, String> rawVars) {
        System.out.println("🔧 Resolving Configuration exactly to style: " + style);
        
        Map<String, String> resolved = new HashMap<>();
        
        if ("SPRING_DATASOURCE".equals(style)) {
            if (rawVars.containsKey("SPRING_DATASOURCE_URL")) {
                resolved.put("SPRING_DATASOURCE_URL", rawVars.get("SPRING_DATASOURCE_URL"));
            }
            if (rawVars.containsKey("SPRING_DATASOURCE_USERNAME")) {
                resolved.put("SPRING_DATASOURCE_USERNAME", rawVars.get("SPRING_DATASOURCE_USERNAME"));
            }
        } else if ("DATABASE_URL_ONLY".equals(style)) {
            if (rawVars.containsKey("DATABASE_URL")) {
                resolved.put("DATABASE_URL", rawVars.get("DATABASE_URL"));
            }
        } else {
            resolved.putAll(rawVars); // Generic fallback
        }
        
        return ConfigurationContract.builder()
                .configurationStyle(style)
                .resolvedVariables(resolved)
                .build();
    }
}
