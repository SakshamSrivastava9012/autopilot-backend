package com.autopilot.service.deployment;

import com.autopilot.analyzer.RepoAnalyzerService;
import com.autopilot.analyzer.model.RepoAnalysisResult;
import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.dto.TerraformResult;
import com.autopilot.entity.Deployment;
import com.autopilot.enums.DeploymentStatus;
import com.autopilot.repository.DeploymentRepository;
import com.autopilot.service.aws.DockerPushService;
import com.autopilot.service.infrastructure.ec2.SSMDeployService;
import com.autopilot.service.terraform.TerraformService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeploymentPipelineService {

    private final RepoAnalyzerService repoAnalyzerService;
    private final DockerfileGenerator dockerfileGenerator;
    private final DockerBuilder dockerBuilder;
    private final DockerPushService dockerPushService;
    private final TerraformService terraformService;
    private final SSMDeployService ssmDeployService;
    private final DeploymentRepository deploymentRepository;

    private static final String WORKSPACE_ROOT = "/tmp/autopilot-workspaces";

    public void execute(Deployment deployment) {

        Path workspace = null;

        try {

            System.out.println("Starting deployment: " + deployment.getId());

            // 🔹 CLONE
            deployment.setStatus(DeploymentStatus.CLONING.name());
            deploymentRepository.save(deployment);

            Files.createDirectories(Path.of(WORKSPACE_ROOT));

            workspace = Path.of(WORKSPACE_ROOT, UUID.randomUUID().toString());
            Files.createDirectories(workspace);

            cloneRepo(deployment, workspace);

            System.out.println("Repository cloned");

            // 🔹 ANALYZE
            deployment.setStatus(DeploymentStatus.ANALYZING.name());
            deploymentRepository.save(deployment);

            RepoAnalysisResult analysis =
                    repoAnalyzerService.analyzeWorkspace(workspace);

            List<ServiceConfig> services = analysis.getServices();

            if (services == null || services.isEmpty()) {
                throw new RuntimeException("No deployable services detected");
            }

            // 🔹 BUILD + PUSH
            for (ServiceConfig service : services) {

                Path servicePath = workspace.resolve(service.getPath());

                if (Files.isRegularFile(servicePath)) {
                    servicePath = servicePath.getParent();
                }

                service.setPath(servicePath.toString());

                if (!service.isDockerfileExists()) {
                    dockerfileGenerator.generate(service);
                }

                deployment.setStatus(DeploymentStatus.BUILDING_IMAGE.name());
                deploymentRepository.save(deployment);

                String imageName =
                        dockerBuilder.build(service, deployment.getId());

                System.out.println("Docker image built: " + imageName);

                deployment.setStatus(DeploymentStatus.PUSHING_IMAGE.name());
                deploymentRepository.save(deployment);

                String imageUri =
                        dockerPushService.pushImage(
                                deployment.getAwsRoleArn(),
                                deployment.getAwsRegion(),
                                imageName
                        );

                deployment.setImageUri(imageUri);
                deploymentRepository.save(deployment); // 🔥 ensure persistence

                System.out.println("Image pushed to ECR: " + imageUri);
            }

            // 🔹 INFRA PROVISION
            deployment.setStatus(DeploymentStatus.PROVISIONING_INFRA.name());
            deploymentRepository.save(deployment);

            TerraformResult result =
                    terraformService.provisionInfrastructure(
                            deployment.getAwsRoleArn(),
                            deployment.getAwsRegion(),
                            deployment.getExpectedUsers(),
                            deployment.getPort(),
                            deployment.getId()
                    );

            // ✅ SET BOTH VALUES
            deployment.setEc2InstanceId(result.getInstanceId());
            deployment.setPublicIp(result.getPublicIp());

            // 🔥 CRITICAL SAVE (you were missing this earlier)
            deploymentRepository.save(deployment);

            System.out.println("EC2 instance created: " + result.getInstanceId());
            System.out.println("Public IP: " + result.getPublicIp());

            // 🔹 WAIT FOR BOOT
            System.out.println("Waiting for EC2 basic boot...");
            Thread.sleep(90000);

            // 🔹 DEPLOY
            deployment.setStatus(DeploymentStatus.DEPLOYING.name());
            deploymentRepository.save(deployment);

            ssmDeployService.deployContainer(
                    result.getInstanceId(), // ✅ use from result
                    deployment.getImageUri(),
                    deployment.getPort(),
                    deployment.getAwsRegion(),
                    deployment.getAwsRoleArn()
            );

            // 🔹 SUCCESS
            deployment.setStatus(DeploymentStatus.RUNNING.name());
            deploymentRepository.save(deployment);

            System.out.println("Deployment completed successfully");

            // 🔥 OPTIONAL: Print access URL
            System.out.println(
                    "App URL: http://" + deployment.getPublicIp() + ":" + deployment.getPort()
            );

        } catch (Exception e) {

            e.printStackTrace();

            deployment.setStatus(DeploymentStatus.FAILED.name());
            deployment.setLogs(e.getMessage());
            deploymentRepository.save(deployment);

        } finally {

            cleanup(workspace);
        }
    }

    private void cloneRepo(Deployment deployment, Path workspace) throws Exception {

        String command =
                "git clone --depth=1 -b "
                        + deployment.getBranch()
                        + " "
                        + deployment.getRepoUrl()
                        + " "
                        + workspace;

        Process process =
                Runtime.getRuntime().exec(
                        new String[]{"bash", "-c", command}
                );

        int exit = process.waitFor();

        if (exit != 0) {
            throw new RuntimeException("Git clone failed");
        }
    }

    private void cleanup(Path workspace) {

        try {

            if (workspace == null) return;

            Files.walk(workspace)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> path.toFile().delete());

        } catch (Exception ignored) {}
    }
}