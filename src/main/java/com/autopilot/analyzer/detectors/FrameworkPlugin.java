package com.autopilot.analyzer.detectors;

import com.autopilot.analyzer.model.ServiceConfig;
import java.util.List;

public interface FrameworkPlugin {

    ServiceConfig detect(List<String> files);
}