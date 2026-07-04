package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class RustAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        return relativeFiles.stream().anyMatch(f -> f.endsWith("Cargo.toml"));
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "Rust";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "Rust Cargo backend binary application.";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        return DeploymentManifest.builder()
                .framework("rust")
                .runtime("Rust 1.78")
                .packageManager("cargo")
                .installCommand("cargo fetch")
                .buildCommand("cargo build --release")
                .startCommand("./target/release/app")
                .outputDirectory("target/release")
                .healthCheckPath("/health")
                .port(8080)
                .environmentVariables(envVars)
                .build();
    }
}
