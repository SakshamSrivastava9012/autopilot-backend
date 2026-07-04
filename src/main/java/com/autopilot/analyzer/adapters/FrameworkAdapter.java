package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface FrameworkAdapter {
    boolean matches(Path workspace, List<String> relativeFiles);
    String detect(Path workspace, List<String> relativeFiles);
    String buildInfo(Path workspace, List<String> relativeFiles);
    DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars);
}
