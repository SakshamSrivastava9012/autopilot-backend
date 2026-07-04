package com.autopilot.service.deployment.v5.runtime.proxy.policy;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Security, CORS, and proxy headers policy.
 *
 * @since V5.4 — ADR-013
 */
@Value
@Builder
public class HeaderPolicy {
    boolean hstsEnabled;
    String hstsDirective;
    String contentSecurityPolicy;
    boolean corsEnabled;
    String allowOrigin;
    boolean xForwardedHeadersEnabled;
    Map<String, String> customHeaders;
}
