package com.autopilot.service.deployment.v5.inspection;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable runtime manifest produced by inspecting a built container image.
 * This is the definitive description of an image's runtime characteristics.
 * No downstream stage may modify the image based on this inspection.
 *
 * @since V5.3 — ADR-006
 */
@Value
@Builder
public class RuntimeManifestV5 {
    String runtimeType;               // JRE, NODE, PYTHON, GO, RUST, STATIC, CUSTOM
    List<Integer> ports;
    String healthStrategy;            // ACTUATOR, HTTP_ROOT, TCP, PROCESS_ALIVE, DOCKER_HEALTHCHECK
    String healthEndpoint;            // e.g. "/actuator/health", "/" — null if not HTTP
    List<String> startupHints;        // Log patterns indicating readiness

    Set<String> runtimeCapabilities;  // SPA, SSR, WEBSOCKET, STATIC_ASSETS, OAUTH, etc.

    // Filesystem layout
    List<String> staticRoots;         // e.g. ["/app/public", "/app/build"]
    List<String> dynamicRoots;        // e.g. ["/app/src", "/tmp"]

    // Process metadata
    String entrypoint;
    String cmd;
    String workingDirectory;
    String runtimeUser;

    // Container metadata
    Map<String, String> labels;
    Map<String, String> environmentDefaults;
    boolean hasDockerHealthcheck;

    List<String> warnings;
}
