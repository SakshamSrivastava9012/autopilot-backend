package com.autopilot.service;

import com.autopilot.dto.DeployRequest;
import com.autopilot.entity.Deployment;
import com.autopilot.entity.User;
import com.autopilot.enums.DeploymentStatus;
import com.autopilot.queue.RedisQueueService;
import com.autopilot.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeploymentService implements DeploymentServiceInterface {

    private final DeploymentRepository deploymentRepository;
    private final RedisQueueService redisQueueService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public Deployment createDeployment(DeployRequest request, User user) {

        Deployment deployment = new Deployment();

        deployment.setUser(user);  // ← ownership link
        deployment.setProjectName(request.getProjectName());
        deployment.setRepoUrl(request.getRepoUrl());
        deployment.setBranch(request.getBranch());
        deployment.setPort(request.getPort());
        deployment.setEnvironment(request.getEnvironment());
        deployment.setExpectedUsers(request.getExpectedUsers());
        deployment.setAwsRoleArn(request.getAwsRoleArn());
        deployment.setAwsRegion(request.getAwsRegion());

        if (request.getEnvVars() != null && !request.getEnvVars().isEmpty()) {
            try {
                deployment.setCustomEnvVarsJson(objectMapper.writeValueAsString(request.getEnvVars()));
            } catch (Exception e) {
                // Ignore serialization error for now
            }
        }

        deployment.setStatus(DeploymentStatus.PENDING.name());

        deployment = deploymentRepository.save(deployment);

        // enqueue async pipeline
        redisQueueService.enqueue(deployment.getId());

        return deployment;
    }

    @Override
    public Deployment getDeployment(String id, User user) {

        // ✅ Ownership check — user can only fetch their own deployments
        return deploymentRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Deployment not found"));
    }

    @Override
    public List<Deployment> getUserDeployments(User user) {
        return deploymentRepository.findByUserId(user.getId());
    }
}