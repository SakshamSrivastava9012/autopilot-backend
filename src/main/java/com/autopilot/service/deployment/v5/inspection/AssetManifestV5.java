package com.autopilot.service.deployment.v5.inspection;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Immutable asset manifest entry discovered during runtime inspection.
 * Describes assets as they exist inside the built container — never rewrites them.
 *
 * @since V5.3 — ADR-006
 */
@Value
@Builder
public class AssetManifestV5 {
    String logicalPath;           // e.g. "static/js/main.abc123.js"
    String containerPath;         // e.g. "/app/build/static/js/main.abc123.js"
    String mimeType;              // e.g. "application/javascript"
    boolean cacheable;
    boolean requiresBasePath;     // Does this asset reference relative paths that need prefix?
    boolean generated;            // Was this asset produced by the build (vs checked in)?
    boolean runtimeAccessible;    // Can the container serve this at runtime?
}
