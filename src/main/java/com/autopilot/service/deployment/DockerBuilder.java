package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds Docker images and captures full build output for self-healing.
 *
 * When a build fails, the error logs are preserved in the thrown exception
 * so the pipeline can classify the error and retry with a fix.
 */
@Component
public class DockerBuilder {

    /**
     * Result of a Docker build attempt.
     */
    public static class BuildResult {
        public final boolean success;
        public final String imageName;
        public final List<String> logs;
        public final String errorCategory; // VERSION_MISMATCH, DEPENDENCY_ERROR, FILE_NOT_FOUND, etc.

        public BuildResult(boolean success, String imageName, List<String> logs, String errorCategory) {
            this.success = success;
            this.imageName = imageName;
            this.logs = logs;
            this.errorCategory = errorCategory;
        }
    }

    /**
     * Build Docker image. Returns BuildResult instead of throwing on failure.
     * This allows the pipeline to inspect logs and apply self-healing.
     */
    public BuildResult buildSafe(ServiceConfig service, String deploymentId) {
        return buildSafeSuffix(service, deploymentId, "");
    }

    public BuildResult buildSafeSuffix(ServiceConfig service, String deploymentId, String suffix) {

        String imageName = "autopilot-" + deploymentId + suffix;
        List<String> buildLogs = new ArrayList<>();

        Path path = Path.of(service.getPath());

        try {
            if (Files.isRegularFile(path)) {
                path = path.getParent();
            }
        } catch (Exception e) {
            // path resolution failed — use as-is
        }

        String command = "docker build --no-cache -t " + imageName + " " + path;

        System.out.println("🚀 Running: " + command);
        buildLogs.add("CMD: " + command);

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                buildLogs.add(line);
            }

            int exit = process.waitFor();

            if (exit != 0) {
                String errorCategory = classifyError(buildLogs);
                System.err.println("❌ Docker build FAILED — Error category: " + errorCategory);
                return new BuildResult(false, imageName, buildLogs, errorCategory);
            }

            System.out.println("✅ Docker image built: " + imageName);
            return new BuildResult(true, imageName, buildLogs, null);

        } catch (Exception e) {
            buildLogs.add("EXCEPTION: " + e.getMessage());
            return new BuildResult(false, imageName, buildLogs, "UNKNOWN");
        }
    }

    /**
     * Legacy build method — throws on failure. Used by simple callers.
     */
    public String build(ServiceConfig service, String deploymentId) throws Exception {
        BuildResult result = buildSafe(service, deploymentId);
        if (!result.success) {
            String lastLines = String.join("\n", result.logs.subList(
                    Math.max(0, result.logs.size() - 20), result.logs.size()
            ));
            throw new RuntimeException(
                    "❌ Docker build failed [" + result.errorCategory + "] for image: " + result.imageName
                            + "\n--- LAST 20 LINES ---\n" + lastLines
            );
        }
        return result.imageName;
    }

    /**
     * Classify a Docker build error by scanning the build logs.
     * Used by the self-healing engine to determine the fix strategy.
     */
    private String classifyError(List<String> logs) {
        String combined = String.join("\n", logs).toLowerCase();

        if (combined.contains("release version") && combined.contains("not supported")) {
            return "VERSION_MISMATCH";
        }
        if (combined.contains("could not resolve dependencies") || combined.contains("unable to resolve")) {
            return "DEPENDENCY_ERROR";
        }
        if (combined.contains("no such file or directory") || combined.contains("file not found")) {
            return "FILE_NOT_FOUND";
        }
        if (combined.contains("port") && combined.contains("already")) {
            return "PORT_CONFLICT";
        }
        if (combined.contains("permission denied")) {
            return "PERMISSION_ERROR";
        }
        if (combined.contains("npm err") || combined.contains("npm error")) {
            return "NPM_ERROR";
        }
        if (combined.contains("pip") && combined.contains("error")) {
            return "PIP_ERROR";
        }
        if (combined.contains("connection") && (combined.contains("timeout") || combined.contains("refused"))) {
            return "NETWORK_ERROR";
        }
        if (combined.contains("out of memory") || combined.contains("oom")) {
            return "OOM_ERROR";
        }

        return "UNKNOWN";
    }
}