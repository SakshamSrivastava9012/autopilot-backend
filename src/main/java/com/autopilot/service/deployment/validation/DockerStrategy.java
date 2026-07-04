package com.autopilot.service.deployment.validation;

public class DockerStrategy {
    private final String dockerfilePath;
    private final String buildContext;

    public DockerStrategy(String dockerfilePath, String buildContext) {
        this.dockerfilePath = dockerfilePath;
        this.buildContext = buildContext;
    }

    public String getDockerfilePath() {
        return dockerfilePath;
    }

    public String getBuildContext() {
        return buildContext;
    }
}
