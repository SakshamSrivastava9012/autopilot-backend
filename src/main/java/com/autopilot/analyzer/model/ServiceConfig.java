package com.autopilot.analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceConfig {

    private String name;

    private String framework;

    private String path;

    private String buildCommand;

    private String startCommand;

    private Integer port;

    private boolean dockerfileExists;

    // Database detection
    private String requiresDatabase; // e.g., "POSTGRES", "MYSQL", "MONGO"
    private String databaseEnvVarName; // e.g., "DATABASE_URL" or "SPRING_DATASOURCE_URL"

    // Failsafe Metadata
    private String strategyUsed;   // DOCKERFILE, TEMPLATE, AI_GENERATED, FALLBACK
    private String language;       // java, javascript, python, go, rust
    private String runtimeVersion; // e.g. "21", "20", "3.10"

    // Confidence scoring (0-100)
    private Integer confidence;

    // Detected environment variables
    private List<String> envVariables;

    // Build steps for multi-step builds
    private List<String> buildSteps;
}