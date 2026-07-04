package com.autopilot.service.deployment.intelligence.v5.model;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Describes a secret or credential reference discovered in the repository.
 * The engine never reads, stores, or injects actual credential values.
 *
 * @since V5
 */
@Value
@Builder
public class SecretDefinition {
    String key;              // e.g. "JWT_SECRET", "STRIPE_API_KEY"
    String provider;         // e.g. "OAUTH", "STRIPE", "AWS", "OPENAI"
    String status;           // HARDCODED, ENVIRONMENT, MISSING, PLACEHOLDER
    String source;           // e.g. ".env:7" or "application.yml:45"
    List<String> evidence;
}
