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

import com.autopilot.service.aws.CredentialResolverService;
import com.autopilot.service.terraform.TerraformService;
import java.util.concurrent.CompletableFuture;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeploymentService implements DeploymentServiceInterface {

    private final DeploymentRepository deploymentRepository;
    private final RedisQueueService redisQueueService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final TerraformService terraformService;
    private final CredentialResolverService credentialResolverService;

    @Override
    public Deployment createDeployment(DeployRequest request, User user) {

        // ── INPUT VALIDATION ────────────────────────────────────────────
        if (request.getRepoUrl() == null || request.getRepoUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "repoUrl is required");
        }

        // Prevent SSRF — only allow known git hosting providers
        String repoUrl = request.getRepoUrl().trim();
        if (!repoUrl.matches("https://(github\\.com|gitlab\\.com|bitbucket\\.org|codeberg\\.org)/.*\\.git$")
            && !repoUrl.matches("https://(github\\.com|gitlab\\.com|bitbucket\\.org|codeberg\\.org)/[^/]+/[^/]+/?$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only GitHub, GitLab, Bitbucket, and Codeberg repos are supported. URL must start with https://");
        }

        // Default deployment mode
        String mode = request.getDeploymentMode();
        if (mode == null || mode.isBlank()) {
            mode = "BYOC"; // backward compatibility
        }
        mode = mode.toUpperCase();
        if (!mode.equals("MANAGED") && !mode.equals("BYOC")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "deploymentMode must be MANAGED or BYOC");
        }

        // BYOC requires a valid ARN
        if ("BYOC".equals(mode)) {
            if (request.getAwsRoleArn() == null || request.getAwsRoleArn().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "awsRoleArn is required for BYOC mode");
            }
            if (!request.getAwsRoleArn().matches("arn:aws:iam::\\d{12}:role/.+")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid ARN format. Expected: arn:aws:iam::<account-id>:role/<role-name>");
            }
        }

        // ── BUILD ENTITY ────────────────────────────────────────────────
        Deployment deployment = new Deployment();

        deployment.setUser(user);
        deployment.setProjectName(request.getProjectName());
        deployment.setRepoUrl(repoUrl);
        deployment.setBranch(request.getBranch() != null ? request.getBranch() : "main");
        deployment.setPort(request.getPort());
        deployment.setEnvironment(request.getEnvironment());
        deployment.setExpectedUsers(request.getExpectedUsers());
        deployment.setDeploymentMode(mode);
        deployment.setAwsRoleArn(request.getAwsRoleArn());
        deployment.setAwsRegion(request.getAwsRegion() != null ? request.getAwsRegion() : "ap-south-1");
        deployment.setInstanceTypeOverride(request.getInstanceTypeOverride());

        if (request.getEnvVars() != null && !request.getEnvVars().isEmpty()) {
            try {
                deployment.setCustomEnvVarsJson(objectMapper.writeValueAsString(request.getEnvVars()));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid envVars format: " + e.getMessage());
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

    @Override
    public Deployment deleteDeployment(String id, User user) {
        // Ownership check
        Deployment deployment = deploymentRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Deployment not found"));

        if (DeploymentStatus.DESTROYING.name().equals(deployment.getStatus()) ||
                DeploymentStatus.DESTROYED.name().equals(deployment.getStatus())) {
            return deployment;
        }

        deployment.setStatus(DeploymentStatus.DESTROYING.name());
        deployment = deploymentRepository.save(deployment);

        final Deployment finalDeployment = deployment;

        CompletableFuture.runAsync(() -> {
            try {
                // Resolve AWS credentials and region dynamically
                CredentialResolverService.ResolvedCredentials credentials = credentialResolverService.resolve(finalDeployment);
                com.autopilot.dto.AwsCredentialsDto credsDto = credentials.credentials();
                String awsRegion = credentials.region();

                int port = finalDeployment.getAssignedPort() != null ? finalDeployment.getAssignedPort() : 80;

                // Execute terraform destroy
                terraformService.destroyInfrastructure(credsDto, awsRegion, port, finalDeployment.getId());

                finalDeployment.setStatus(DeploymentStatus.DESTROYED.name());
                deploymentRepository.save(finalDeployment);
            } catch (Exception e) {
                System.err.println("Teardown failed for deployment " + finalDeployment.getId() + ": " + e.getMessage());
                finalDeployment.setStatus(DeploymentStatus.FAILED.name());
                deploymentRepository.save(finalDeployment);
            }
        });

        return deployment;
    }
}