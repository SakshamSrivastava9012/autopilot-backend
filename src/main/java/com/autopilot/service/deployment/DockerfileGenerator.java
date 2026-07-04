package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.FrameworkType;
import com.autopilot.analyzer.model.PackageManager;
import com.autopilot.analyzer.model.RuntimeType;
import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.service.deployment.strategies.RuntimeStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Production-grade Dockerfile generator refactored to use the Strategy Pattern.
 */
@Component
@RequiredArgsConstructor
public class DockerfileGenerator {

    private final List<RuntimeStrategy> strategies;
    private final DockerTemplateLoader templateLoader;
    private final StellarDockerService stellarDockerService;

    public void generate(ServiceConfig service) throws Exception {
        // Convert ServiceConfig to FrameworkMetadata for backward compatibility
        FrameworkMetadata metadata = convertToMetadata(service);
        generateForMetadata(metadata, service.getPath());
    }

    public void generateForMetadata(FrameworkMetadata metadata, String targetPath) throws Exception {
        System.out.println("━━━ DockerfileGenerator (Strategy) ━━━━━━━━━━━━━━━━━━");
        System.out.println("  Framework: " + metadata.getFrameworkType());
        System.out.println("  Runtime:   " + metadata.getRuntimeType());
        System.out.println("  Build:     " + metadata.getBuildCommand());
        System.out.println("  Start:     " + metadata.getStartCommand());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (metadata.isDockerfileExists()) {
            System.out.println("✅ TIER 1: Native Dockerfile found → skipping generation");
            return;
        }

        // Resolve Dockerfile via strategy
        String dockerfile = null;
        RuntimeStrategy strategy = strategies.stream()
                .filter(s -> s.supports(metadata.getRuntimeType()))
                .findFirst()
                .orElse(null);

        if (strategy != null) {
            dockerfile = strategy.generateDockerfile(metadata);
        }

        // If strategy failed or generated nothing, use Generic fallback strategy
        if (dockerfile == null || dockerfile.isBlank()) {
            System.out.println("🛡️ Fallback: Using Generic strategy");
            RuntimeStrategy fallbackStrategy = strategies.stream()
                    .filter(s -> s.supports(RuntimeType.GENERIC))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Generic strategy not found"));
            dockerfile = fallbackStrategy.generateDockerfile(metadata);
        }

        // Write Dockerfile to directory
        Path servicePath = Path.of(targetPath).toAbsolutePath().normalize();
        if (Files.isRegularFile(servicePath)) {
            servicePath = servicePath.getParent();
        }

        Files.createDirectories(servicePath);
        Path dockerfilePath = servicePath.resolve("Dockerfile");

        System.out.println("📝 Writing Dockerfile to: " + dockerfilePath);
        System.out.println("── DOCKERFILE CONTENT ──────────────────────────────");
        System.out.println(dockerfile);
        System.out.println("────────────────────────────────────────────────────");

        try (FileWriter writer = new FileWriter(dockerfilePath.toFile())) {
            writer.write(dockerfile);
        }
    }

    private FrameworkMetadata convertToMetadata(ServiceConfig service) {
        // Map ServiceConfig string types to enums safely
        FrameworkType ft = FrameworkType.GENERIC;
        try {
            if (service.getFramework() != null) {
                ft = FrameworkType.valueOf(service.getFramework().toUpperCase().replace("-", "_"));
            }
        } catch (Exception ignored) {}

        RuntimeType rt = RuntimeType.GENERIC;
        if (service.getDeploymentManifest() != null && service.getDeploymentManifest().getRuntime() != null) {
            String runtime = service.getDeploymentManifest().getRuntime().toUpperCase();
            if (runtime.contains("STATIC")) rt = RuntimeType.STATIC;
            else if (runtime.contains("SSR")) rt = RuntimeType.SSR;
            else if (runtime.contains("NODE")) rt = RuntimeType.NODE_SERVER;
            else if (runtime.contains("JAVA")) rt = RuntimeType.JAVA_JAR;
            else if (runtime.contains("PYTHON")) rt = RuntimeType.PYTHON;
            else if (runtime.contains("GO")) rt = RuntimeType.GO_BINARY;
            else if (runtime.contains("RUST")) rt = RuntimeType.RUST_BINARY;
        } else {
            String lang = service.getLanguage() != null ? service.getLanguage().toLowerCase() : "";
            if (lang.contains("java")) rt = RuntimeType.JAVA_JAR;
            else if (lang.contains("javascript") || lang.contains("node") || lang.contains("typescript")) {
                rt = RuntimeType.NODE_SERVER;
            } else if (lang.contains("python")) rt = RuntimeType.PYTHON;
            else if (lang.contains("go")) rt = RuntimeType.GO_BINARY;
            else if (lang.contains("rust")) rt = RuntimeType.RUST_BINARY;
        }

        PackageManager pm = PackageManager.NONE;
        if (service.getDeploymentManifest() != null && service.getDeploymentManifest().getPackageManager() != null) {
            try {
                pm = PackageManager.valueOf(service.getDeploymentManifest().getPackageManager().toUpperCase());
            } catch (Exception ignored) {}
        } else {
            String build = service.getBuildCommand() != null ? service.getBuildCommand().toLowerCase() : "";
            if (build.contains("mvn")) pm = PackageManager.MAVEN;
            else if (build.contains("gradle")) pm = PackageManager.GRADLE;
            else if (build.contains("npm")) pm = PackageManager.NPM;
            else if (build.contains("yarn")) pm = PackageManager.YARN;
            else if (build.contains("pnpm")) pm = PackageManager.PNPM;
            else if (build.contains("pip")) pm = PackageManager.PIP;
        }

        FrameworkMetadata metadata = FrameworkMetadata.builder()
                .name(service.getName())
                .frameworkType(ft)
                .runtimeType(rt)
                .packageManager(pm)
                .buildCommand(service.getBuildCommand())
                .startCommand(service.getStartCommand())
                .outputDirectory(service.getDeploymentManifest() != null ? service.getDeploymentManifest().getOutputDirectory() : "dist")
                .port(service.getPort() != null ? service.getPort() : 8080)
                .healthCheckPath(service.getDeploymentManifest() != null ? service.getDeploymentManifest().getHealthCheckPath() : "/")
                .language(service.getLanguage())
                .defaultRuntimeVersion(service.getRuntimeVersion())
                .dockerfileExists(service.isDockerfileExists())
                .basePath(service.getBasePath())
                .build();
        metadata.validate();
        return metadata;
    }
}