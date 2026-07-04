package com.autopilot.analyzer.runtime;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DockerConfiguration {
    private String baseImage;
    private List<String> buildSteps;
    private String startCommand;
    private int port;
    private String outputDir;
}
