package com.autopilot.service;

import com.autopilot.dto.DeployRequest;
import com.autopilot.entity.Deployment;
import com.autopilot.enums.DeploymentStatus;
import com.autopilot.queue.RedisQueueService;
import com.autopilot.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeploymentService implements DeploymentServiceInterface {

    private final DeploymentRepository deploymentRepository;
    private final RedisQueueService redisQueueService;

    @Override
    public Deployment createDeployment(DeployRequest request) {

        Deployment deployment = new Deployment();

        deployment.setProjectName(request.getProjectName());
        deployment.setRepoUrl(request.getRepoUrl());
        deployment.setBranch(request.getBranch());
        deployment.setPort(request.getPort());
        deployment.setEnvironment(request.getEnvironment());
        deployment.setExpectedUsers(request.getExpectedUsers());
        deployment.setAwsRoleArn(request.getAwsRoleArn());
        deployment.setAwsRegion(request.getAwsRegion());

        deployment.setStatus(DeploymentStatus.PENDING.name());

        deployment = deploymentRepository.save(deployment);

        redisQueueService.enqueue(deployment.getId());

        return deployment;
    }

    @Override
    public Deployment getDeployment(String id) {
        return deploymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Deployment not found"));
    }

    @Override
    public List<Deployment> getAllDeployments() {
        return deploymentRepository.findAll();
    }
}
