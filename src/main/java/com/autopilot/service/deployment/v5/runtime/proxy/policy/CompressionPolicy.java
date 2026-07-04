package com.autopilot.service.deployment.v5.runtime.proxy.policy;

import lombok.Builder;
import lombok.Value;

/**
 * Compression Policy (Gzip, Brotli).
 *
 * @since V5.4 — ADR-013
 */
@Value
@Builder
public class CompressionPolicy {
    boolean gzipEnabled;
    boolean brotliEnabled;
    int gzipLevel;
    int brotliLevel;
}
