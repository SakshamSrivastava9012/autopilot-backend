package com.autopilot.service.deployment.v5.negotiation;

import com.autopilot.service.deployment.intelligence.v5.model.RepositoryModelV5;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Determines exactly which configuration style a repository expects.
 * Produces a single ConfigurationContractV5 — never mixes styles.
 *
 * Pure data analysis — no filesystem access, no environment injection.
 *
 * @since V5.2 — ADR-005
 */
@Service
public class ConfigurationNegotiationEngineV5 {

    /**
     * Negotiate configuration style from immutable RepositoryModelV5.
     * Returns exactly one style per service.
     */
    public ConfigurationContractV5 negotiate(RepositoryModelV5 model) {
        System.out.println("⚙️ Configuration Negotiation Engine V5 — Negotiating config style...");

        String style;
        String envModel;
        int confidence;
        List<String> warnings = new ArrayList<>();

        Set<String> frameworks = model.getFrameworks();
        Set<String> languages = model.getLanguages();

        if (frameworks.contains("Next.js") || frameworks.contains("Nuxt")) {
            style = "NEXT_PUBLIC_ENV";
            envModel = "Next.js / Nuxt .env";
            confidence = 95;
        } else if (languages.contains("Java")) {
            style = "SPRING_DATASOURCE";
            envModel = "Spring Boot application.yml";
            confidence = 95;
        } else if (frameworks.contains("Django")) {
            style = "DATABASE_URL";
            envModel = "Django settings.py / dj-database-url";
            confidence = 90;
        } else if (frameworks.contains("Laravel")) {
            style = "DB_HOST";
            envModel = "Laravel .env";
            confidence = 90;
        } else if (frameworks.contains("Rails")) {
            style = "DATABASE_URL";
            envModel = "Rails database.yml / DATABASE_URL";
            confidence = 85;
        } else if (languages.contains("JavaScript") || languages.contains("TypeScript")) {
            style = "DATABASE_URL";
            envModel = "Node.js .env / DATABASE_URL";
            confidence = 85;
        } else if (languages.contains("Python")) {
            style = "DATABASE_URL";
            envModel = "Python DATABASE_URL";
            confidence = 80;
        } else if (languages.contains("Go")) {
            style = "DATABASE_URL";
            envModel = "Go env / DATABASE_URL";
            confidence = 75;
        } else if (languages.contains("Rust")) {
            style = "DATABASE_URL";
            envModel = "Rust dotenv / DATABASE_URL";
            confidence = 75;
        } else {
            style = "GENERIC_ENV";
            envModel = "Generic .env";
            confidence = 50;
            warnings.add("Could not determine specific configuration style. Falling back to generic.");
        }

        System.out.println("   Negotiated Style: " + style + " (confidence=" + confidence + "%)");

        return ConfigurationContractV5.builder()
                .frameworkStyle(style)
                .environmentModel(envModel)
                .variables(Collections.emptyMap())      // Populated by provisioning layer later
                .removedVariables(Collections.emptySet())
                .generatedVariables(Collections.emptySet())
                .warnings(Collections.unmodifiableList(warnings))
                .confidence(confidence)
                .build();
    }
}
