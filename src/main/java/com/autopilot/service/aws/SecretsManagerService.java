package com.autopilot.service.aws;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.intelligence.model.ConfigEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AWS Secrets Manager integration.
 *
 * Stores detected secrets from the Config Intelligence Pipeline
 * into AWS Secrets Manager and returns the Secret ARN.
 */
@Service
@RequiredArgsConstructor
public class SecretsManagerService {

    /**
     * Store all detected secrets as a single AWS Secrets Manager secret.
     *
     * @param deploymentId Unique deployment identifier (used as secret name)
     * @param secrets      List of detected ConfigEntry objects marked as secret
     * @param creds        AWS Credentials DTO
     * @param region       AWS Region
     * @return The ARN of the created secret
     */
    public String storeSecrets(
            String deploymentId,
            List<ConfigEntry> secrets,
            AwsCredentialsDto creds,
            String region
    ) {
        if (secrets == null || secrets.isEmpty()) {
            System.out.println("🔒 SecretsManager: No secrets to store");
            return null;
        }

        try {
            SecretsManagerClient client = buildClient(creds, region);

            String secretName = "autopilot/" + deploymentId;

            // Build JSON key-value map of all secrets
            Map<String, String> secretMap = new LinkedHashMap<>();
            for (ConfigEntry secret : secrets) {
                String key = secret.getNormalizedKey() != null ? secret.getNormalizedKey() : secret.getKey();
                secretMap.put(key, secret.getValue());
            }

            String secretJson = toJson(secretMap);

            // Try to create — if it already exists, update it
            String arn;
            try {
                CreateSecretResponse response = client.createSecret(
                        CreateSecretRequest.builder()
                                .name(secretName)
                                .description("Autopilot deployment secrets for " + deploymentId)
                                .secretString(secretJson)
                                .build()
                );
                arn = response.arn();
                System.out.println("🔒 SecretsManager: Created secret → " + arn);
            } catch (ResourceExistsException e) {
                // Secret already exists — update it
                PutSecretValueResponse response = client.putSecretValue(
                        PutSecretValueRequest.builder()
                                .secretId(secretName)
                                .secretString(secretJson)
                                .build()
                );
                arn = response.arn();
                System.out.println("🔒 SecretsManager: Updated existing secret → " + arn);
            }

            client.close();
            return arn;

        } catch (Exception e) {
            System.err.println("⚠️ SecretsManager: Failed to store secrets — " + e.getMessage());
            // Non-fatal — deployment continues without Secrets Manager
            return null;
        }
    }

    /**
     * Retrieve secrets from AWS Secrets Manager.
     */
    public Map<String, String> getSecrets(String deploymentId, AwsCredentialsDto creds, String region) {
        try {
            SecretsManagerClient client = buildClient(creds, region);

            GetSecretValueResponse response = client.getSecretValue(
                    GetSecretValueRequest.builder()
                            .secretId("autopilot/" + deploymentId)
                            .build()
            );

            client.close();
            return parseJson(response.secretString());

        } catch (ResourceNotFoundException e) {
            return Map.of(); // No secrets stored
        } catch (Exception e) {
            System.err.println("⚠️ SecretsManager: Failed to retrieve secrets — " + e.getMessage());
            return Map.of();
        }
    }

    private SecretsManagerClient buildClient(AwsCredentialsDto creds, String region) {
        if (creds == null) {
            // Fallback to local default credential provider chain
            return SecretsManagerClient.builder()
                    .region(Region.of(region))
                    .build();
        }

        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                creds.getAccessKeyId(),
                creds.getSecretAccessKey(),
                creds.getSessionToken()
        );

        return SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                .build();
    }

    /**
     * Simple JSON serialization without external lib dependency.
     */
    private String toJson(Map<String, String> map) {
        String entries = map.entrySet().stream()
                .map(e -> "\"" + escapeJson(e.getKey()) + "\":\"" + escapeJson(e.getValue()) + "\"")
                .collect(Collectors.joining(","));
        return "{" + entries + "}";
    }

    /**
     * Simple JSON parsing for flat key-value maps.
     */
    private Map<String, String> parseJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;

        // Remove braces
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        // Split by comma, then by colon
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("^\"|\"$", "");
                String value = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(key, value);
            }
        }
        return map;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
