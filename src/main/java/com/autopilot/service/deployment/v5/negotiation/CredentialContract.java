package com.autopilot.service.deployment.v5.negotiation;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable contract describing credential ownership.
 * Never generates, stores, or injects actual credentials.
 *
 * @since V5.2
 */
@Value
@Builder
public class CredentialContract {
    String credentialSource;    // GENERATED, USER, EXTERNAL, VAULT, SECRETS_MANAGER
    boolean requiresRotation;
    boolean masked;             // Must be masked in all diagnostic output
    String provenance;          // Where the credential reference was found
}
