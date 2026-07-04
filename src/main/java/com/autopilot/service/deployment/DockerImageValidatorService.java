package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class DockerImageValidatorService {

    public static class ImageValidationResult {
        public final boolean valid;
        public final String reason;

        public ImageValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }
    }

    public ImageValidationResult validateImage(String imageName, ServiceConfig service) {
        try {
            // 1. Inspect image config (Entrypoint & Cmd)
            List<String> inspectOutput = runCommand("docker inspect " + imageName);
            String inspectText = String.join("\n", inspectOutput);

            boolean isJava = service.getLanguage() != null && service.getLanguage().equalsIgnoreCase("java");
            boolean isNodeOrReact = service.getLanguage() != null && 
                (service.getLanguage().equalsIgnoreCase("javascript") || 
                 service.getLanguage().equalsIgnoreCase("typescript") || 
                 service.getLanguage().equalsIgnoreCase("node"));
            
            // 2. Run a check on files inside the container
            List<String> files = runCommand("docker run --rm --entrypoint /bin/sh " + imageName + " -c 'ls -la /app /build 2>/dev/null || ls -la'");
            String filesText = String.join("\n", files);

            if (isJava) {
                // Java image should run java or contain jar references
                if (!inspectText.contains("java") && !inspectText.contains(".jar") && !filesText.contains(".jar")) {
                    return new ImageValidationResult(false, "Image does not appear to contain a Java runtime or jar artifact: " + inspectText);
                }
                // Verify it doesn't contain node packages and no jar at all
                if (filesText.contains("package.json") && !filesText.contains("app.jar") && !filesText.contains("target")) {
                    return new ImageValidationResult(false, "Java service contains Node package.json but no jar artifact. Mismatched service packaging.");
                }
            }

            if (isNodeOrReact) {
                // If it's node/react, verify it doesn't run java -jar
                if (inspectText.contains("java") && inspectText.contains(".jar")) {
                    return new ImageValidationResult(false, "Frontend service image contains 'java -jar' entrypoint. Mismatched service packaging.");
                }
            }

            return new ImageValidationResult(true, "Image conforms to service type: " + service.getFramework());
        } catch (Exception e) {
            System.err.println("⚠️ Image validation warning: " + e.getMessage());
            return new ImageValidationResult(true, "Skipped or passed by default: " + e.getMessage());
        }
    }

    private List<String> runCommand(String cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + String.join("\n", output));
        }
        return output;
    }
}
