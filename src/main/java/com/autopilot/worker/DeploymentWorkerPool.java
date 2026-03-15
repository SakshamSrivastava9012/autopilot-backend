package com.autopilot.worker;

import com.autopilot.entity.Deployment;
import com.autopilot.queue.RedisQueueService;
import com.autopilot.repository.DeploymentRepository;
import com.autopilot.service.deployment.DeploymentPipelineService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class DeploymentWorkerPool {

    private final RedisQueueService redisQueueService;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentPipelineService pipelineService;
    private static final int WORKER_COUNT = 5;

    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();
    @PostConstruct
    public void startWorkers() {

        for (int i = 0; i < WORKER_COUNT; i++) {

            executor.submit(this::workerLoop);
        }
    }

    private void workerLoop() {

        while (true) {
            try {

                String deploymentId =
                        redisQueueService.dequeueBlocking();

                if (deploymentId == null) {
                    continue;
                }

                Deployment deployment =
                        deploymentRepository.findById(deploymentId)
                                .orElseThrow();

                pipelineService.execute(deployment);

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }
}
