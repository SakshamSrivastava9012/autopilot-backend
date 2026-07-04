package com.autopilot.analyzer.detectors;

import com.autopilot.analyzer.model.FrameworkMetadata;
import java.nio.file.Path;
import java.util.List;

public interface FrameworkDetector {
    boolean matches(Path workspace, List<String> relativeFiles);
    FrameworkMetadata detect(Path workspace, List<String> relativeFiles);
}