package com.autopilot.service.deployment.v5.build;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;

/**
 * Immutable build strategy determined by the BuildStrategyResolver.
 * Describes HOW to build — never executes the build itself.
 *
 * @since V5.3 — ADR-006
 */
@Value
@Builder
public class BuildPlan {
    String strategy;          // DOCKERFILE, BUILDPACK, MAVEN, GRADLE, NPM, VITE, NEXTJS, PYTHON, GO, RUST, STATIC
    String baseImage;         // e.g. "eclipse-temurin:21-jre", "node:20-alpine"
    String buildCommand;      // e.g. "mvn package -DskipTests", "npm run build"
    String startCommand;      // e.g. "java -jar app.jar", "node server.js"
    String workingDirectory;  // e.g. "/app"
    String dockerfilePath;    // null if auto-generated
    boolean usesCustomDockerfile;
    List<String> buildArgs;
    Map<String, String> labels;
    List<Integer> exposedPorts;
    List<String> warnings;
    int confidence;
}
