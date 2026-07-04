package com.autopilot.service.deployment;

import com.autopilot.entity.Deployment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeploymentPipeline {
    private final DeploymentPipelineService pipelineService;

    public void run(Deployment deployment) {
        pipelineService.execute(deployment);
    }
}
