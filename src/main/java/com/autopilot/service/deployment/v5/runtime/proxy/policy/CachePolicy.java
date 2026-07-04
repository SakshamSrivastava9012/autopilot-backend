package com.autopilot.service.deployment.v5.runtime.proxy.policy;

import lombok.Builder;
import lombok.Value;

/**
 * Cache Policy for static assets, dynamic APIs, OAuth, and media.
 *
 * @since V5.4 — ADR-013
 */
@Value
@Builder
public class CachePolicy {
    String immutableAssetsControl;
    String dynamicApiControl;
    String oauthControl;
    boolean etagEnabled;
}
