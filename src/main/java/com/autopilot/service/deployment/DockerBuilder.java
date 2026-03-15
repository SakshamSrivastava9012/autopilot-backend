package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import org.springframework.stereotype.Component;

@Component
public class DockerBuilder {

    public void build(ServiceConfig service, String deploymentId) throws Exception {

        String imageName =
                "autopilot/" + service.getName() + ":" + deploymentId;

        String command =
                "docker build -t "
                        + imageName
                        + " "
                        + service.getPath();

        Process process =
                Runtime.getRuntime().exec(
                        new String[]{"bash","-c",command});

        process.waitFor();
    }
}