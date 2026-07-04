package com.autopilot.service.deployment.v5.build;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;

/**
 * Immutable artifact produced after a successful build.
 * Represents the container image and its metadata.
 * The image is immutable after this point — no downstream stage may modify it.
 *
 * @since V5.3 — ADR-006
 */
@Value
@Builder
public class BuildArtifact {
    String imageName;
    String imageDigest;
    String imageId;
    String runtime;           // e.g. "jre-21", "node-20", "python-3.12"
    List<Integer> exposedPorts;
    String entrypoint;
    String cmd;
    Map<String, String> labels;
    List<String> buildLogs;
    long buildDurationMs;
    long imageSizeBytes;
    List<String> warnings;
}
