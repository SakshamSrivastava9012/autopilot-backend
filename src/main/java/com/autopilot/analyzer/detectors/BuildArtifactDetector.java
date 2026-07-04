package com.autopilot.analyzer.detectors;

import com.autopilot.analyzer.model.RuntimeType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class BuildArtifactDetector {

    private static final List<String> COMMON_OUTPUT_DIRS = List.of(
            ".next",
            ".output",
            "dist",
            "build",
            "out",
            "target",
            "release",
            "bin",
            "public",
            "server"
    );

    public String detectOutputDirectory(Path workspace) {
        for (String dir : COMMON_OUTPUT_DIRS) {
            Path path = workspace.resolve(dir);
            if (Files.exists(path) && Files.isDirectory(path)) {
                try {
                    // Check if contains files
                    long count = Files.list(path).count();
                    if (count > 0) {
                        return dir;
                    }
                } catch (IOException ignored) {}
            }
        }
        return "."; // fallback to root
    }

    public RuntimeType refineRuntimeType(Path workspace, RuntimeType currentType) {
        String outputDir = detectOutputDirectory(workspace);
        if (".next".equals(outputDir)) {
            return RuntimeType.SSR;
        }
        if (".output".equals(outputDir)) {
            return RuntimeType.SSR;
        }
        if (List.of("dist", "build", "out", "public").contains(outputDir)) {
            if (currentType == RuntimeType.NODE_SERVER || currentType == RuntimeType.SSR || currentType == RuntimeType.STATIC) {
                // If it's a frontend project or was flagged as Node, verify if it should just be STATIC
                return RuntimeType.STATIC;
            }
        }
        return currentType;
    }
}
