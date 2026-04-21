package com.autopilot.analyzer;

import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.service.deployment.StellarClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI Planning Layer — uses Stellar LLM to analyze unknown repositories.
 *
 * STRICT RULES:
 * - AI generates a STRUCTURED PLAN (JSON), NOT a Dockerfile
 * - Output is validated and parsed into ServiceConfig
 * - If parsing fails, returns null (caller falls back to Tier 4)
 * - AI is NEVER the single point of failure
 */
@Service
@RequiredArgsConstructor
public class UniversalAnalyzerService {

    private final StellarClient stellarClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ServiceConfig analyzeTree(List<String> files) {

        // Limit file tree to prevent prompt overflow
        List<String> truncated = files.size() > 200 ? files.subList(0, 200) : files;
        String fileTree = String.join("\n", truncated);

        System.out.println("🌳 AI Analyzer: processing " + files.size() + " files (sent " + truncated.size() + ")");

        String prompt = """
                You are a strict JSON generator. Analyze this file tree and return deployment configuration.

                RULES:
                - Return ONLY valid JSON. No explanation. No markdown.
                - ALL fields are REQUIRED. Use null if unknown.
                - "port" must be a number, not a string.
                - "confidence" is 0 to 100, how confident you are.

                REQUIRED JSON SCHEMA:
                {
                  "name": "project-name",
                  "framework": "spring-boot | express | nextjs | flask | django | fastapi | gin | actix | generic",
                  "language": "java | javascript | typescript | python | go | rust | unknown",
                  "path": ".",
                  "buildCommand": "command to build the project",
                  "startCommand": "command to start the project",
                  "port": 8080,
                  "runtimeVersion": "version number like 17, 20, 3.10",
                  "dockerfileExists": false,
                  "requiresDatabase": null,
                  "databaseEnvVarName": null,
                  "confidence": 70
                }

                FILE TREE:
                """ + fileTree;

        try {
            String json = stellarClient.generateJson(prompt);

            if (json == null || json.isBlank()) {
                System.err.println("🚨 AI returned empty response");
                return null;
            }

            // Strip any markdown fencing the LLM might have added
            json = json.replace("```json", "").replace("```", "").trim();

            System.out.println("📄 AI Raw Response: " + json);

            ServiceConfig config = objectMapper.readValue(json, ServiceConfig.class);

            // Validate critical fields
            if (config.getFramework() == null || config.getFramework().isBlank()) {
                System.err.println("🚨 AI returned no framework");
                return null;
            }

            // Ensure port is set
            if (config.getPort() == null) config.setPort(8080);

            // Ensure path is set
            if (config.getPath() == null || config.getPath().isBlank()) config.setPath(".");

            System.out.println("✅ AI Analysis: " + config.getFramework()
                    + " | " + config.getLanguage()
                    + " | confidence: " + config.getConfidence());

            return config;

        } catch (Exception e) {
            System.err.println("🚨 AI parse failed: " + e.getMessage());
            return null;
        }
    }
}