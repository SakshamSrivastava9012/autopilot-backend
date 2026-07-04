package com.autopilot.service.deployment.runtime.configuration;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

@Service
public class ConfigurationStyleDetector {

    public String detectStyle(Map<String, String> repositoryMetadata) {
        System.out.println("🔍 Detecting Configuration Style...");
        
        if (repositoryMetadata.containsKey("SPRING_BOOT")) {
            System.out.println("  -> Detected Spring Boot application.yml / application.properties");
            return "SPRING_DATASOURCE";
        } else if (repositoryMetadata.containsKey("PRISMA")) {
            System.out.println("  -> Detected Prisma schema.prisma");
            return "DATABASE_URL_ONLY";
        }
        
        return "GENERIC_ENV";
    }

    public List<String> getEvidenceSources() {
        return Arrays.asList("application.yml", "pom.xml", ".env.production");
    }
}
