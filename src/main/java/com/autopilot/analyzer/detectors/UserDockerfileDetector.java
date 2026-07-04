package com.autopilot.analyzer.detectors;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.FrameworkType;
import com.autopilot.analyzer.model.PackageManager;
import com.autopilot.analyzer.model.RuntimeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class UserDockerfileDetector implements FrameworkDetector {

    @Autowired
    @Lazy
    private FrameworkRegistry registry;

    @Override
    public boolean matches(Path workspace, List<String> files) {
        return DetectorUtils.hasFile(files, "Dockerfile");
    }

    @Override
    public FrameworkMetadata detect(Path workspace, List<String> files) {
        FrameworkDetector innerDetector = null;
        if (registry != null) {
            for (FrameworkDetector d : registry.getDetectors()) {
                if (d != this && !(d instanceof GenericFallbackDetector) && d.matches(workspace, files)) {
                    innerDetector = d;
                    break;
                }
            }
        }

        FrameworkMetadata innerMetadata = null;
        if (innerDetector != null) {
            innerMetadata = innerDetector.detect(workspace, files);
        }

        int port = 8080;
        for (String file : files) {
            if (file.endsWith("Dockerfile")) {
                try {
                    String content = Files.readString(workspace.resolve(file));
                    Matcher m = Pattern.compile("EXPOSE\\s+(\\d+)").matcher(content);
                    if (m.find()) {
                        port = Integer.parseInt(m.group(1));
                    }
                } catch (Exception ignored) {}
                break;
            }
        }

        if (innerMetadata != null) {
            return FrameworkMetadata.builder()
                    .name(innerMetadata.getName())
                    .frameworkType(innerMetadata.getFrameworkType())
                    .runtimeType(innerMetadata.getRuntimeType())
                    .packageManager(innerMetadata.getPackageManager())
                    .buildCommand(innerMetadata.getBuildCommand())
                    .startCommand(innerMetadata.getStartCommand())
                    .outputDirectory(innerMetadata.getOutputDirectory())
                    .port(innerMetadata.getPort() != 80 && innerMetadata.getPort() != 8080 ? innerMetadata.getPort() : port)
                    .healthCheckPath(innerMetadata.getHealthCheckPath())
                    .language(innerMetadata.getLanguage())
                    .defaultRuntimeVersion(innerMetadata.getDefaultRuntimeVersion())
                    .dockerfileExists(true)
                    .build();
        }

        return FrameworkMetadata.builder()
                .name(DetectorUtils.deriveServiceName(files, "docker-service"))
                .frameworkType(FrameworkType.DOCKER)
                .runtimeType(RuntimeType.DOCKER)
                .packageManager(PackageManager.NONE)
                .buildCommand("")
                .startCommand("")
                .outputDirectory(".")
                .port(port)
                .healthCheckPath("/")
                .language("docker")
                .defaultRuntimeVersion("latest")
                .dockerfileExists(true)
                .build();
    }
}
