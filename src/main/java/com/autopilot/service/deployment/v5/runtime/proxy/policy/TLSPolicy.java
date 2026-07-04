package com.autopilot.service.deployment.v5.runtime.proxy.policy;

import lombok.Builder;
import lombok.Value;

/**
 * TLS and HTTP-to-HTTPS redirect policy.
 *
 * @since V5.4 — ADR-013
 */
@Value
@Builder
public class TLSPolicy {
    boolean tlsEnabled;
    boolean forceHttpsRedirect;
    String tlsProtocols;
    String certificatePath;
    String privateKeyPath;
}
