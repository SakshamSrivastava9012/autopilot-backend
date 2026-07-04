package com.autopilot.service.deployment.intelligence.v5.model;

import lombok.Builder;
import lombok.Value;

/**
 * Describes a discovered static asset directory.
 * The engine never rewrites or patches assets.
 *
 * @since V5
 */
@Value
@Builder
public class AssetDefinition {
    String path;             // Relative path, e.g. "public/", "static/", "dist/"
    String type;             // PUBLIC, STATIC, BUILD_OUTPUT, NEXT_STATIC, RESOURCES
    String serviceId;        // Which service this belongs to (null for root-level)
    long estimatedSizeBytes;
}
