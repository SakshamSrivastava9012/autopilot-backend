package com.autopilot.service.deployment;

import com.autopilot.analyzer.RepoAnalyzerService;
import com.autopilot.analyzer.model.RepoAnalysisResult;
import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.dto.TerraformResult;
import com.autopilot.entity.Deployment;
import com.autopilot.enums.DeploymentStatus;
import com.autopilot.repository.DeploymentRepository;
import com.autopilot.service.PortAllocatorService;
import com.autopilot.service.aws.DockerPushService;
import com.autopilot.service.infrastructure.NginxConfigService;
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

    private final FrontendPatcherService frontendPatcherService;
    private final RepoAnalyzerService repoAnalyzerService;
    private final DockerfileGenerator dockerfileGenerator;
    private final DockerBuilder dockerBuilder;
    private final DockerPushService dockerPushService;
    private final TerraformService terraformService;
    private final SSMDeployService ssmDeployService;
    private final DeploymentRepository deploymentRepository;
    private final PortAllocatorService portAllocator;
    private final NginxConfigService nginxConfigService;

    private static final String WORKSPACE_ROOT = "/tmp/autopilot-workspaces";

    public void execute(Deployment deployment) {

        Path workspace = null;

        try {

            // ── CLONE ─────────────────────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.CLONING);

            Files.createDirectories(Path.of(WORKSPACE_ROOT));
            workspace = Path.of(WORKSPACE_ROOT, UUID.randomUUID().toString());
            Files.createDirectories(workspace);

            cloneRepo(deployment, workspace);

            // ── ANALYZE ───────────────────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.ANALYZING);

            RepoAnalysisResult analysis = repoAnalyzerService.analyzeWorkspace(workspace);
            ServiceConfig service = analysis.getServices().get(0);

            // service.getPath() is relative — resolve it against workspace to get absolute
            Path servicePath = workspace.resolve(service.getPath()).toAbsolutePath().normalize();

// 🔥 FIX: if analyzer returned a FILE, use its parent directory
            if (Files.isRegularFile(servicePath)) {
                servicePath = servicePath.getParent();
            }

            service.setPath(servicePath.toString());

            int containerPort = service.getPort() != null ? service.getPort() : 3000;
            deployment.setPort(containerPort);

            // FIX 1: 8 chars (dashes stripped) not 5 — prevents nginx block collisions.
            // UUID substring(0,5) gives ~1M combinations and shares prefixes.
            // replace("-","").substring(0,8) gives ~4B combinations.
            String basePath = "/app-" + deployment.getId().replace("-", "").substring(0, 8);
            deployment.setBasePath(basePath);
            deploymentRepository.save(deployment);

            // FIX 2: Patch servicePath, NOT workspace root.
            // service.getPath() points to the actual Next.js/React project directory
            // (may be a subdirectory of the repo, e.g. frontend/ in a monorepo).
            // Patching workspace root writes next.config.js in the wrong place —
            // Docker's COPY . . copies it but npm run build runs in servicePath.
            frontendPatcherService.patchFrontend(servicePath, basePath);

            // FIX 3: Delete .next from servicePath, not workspace root.
            // Same mismatch — stale build lives next to the app's package.json.
            Path nextDir = servicePath.resolve(".next");
            if (Files.exists(nextDir)) {
                System.out.println("Deleting stale .next build at: " + nextDir);
                Files.walk(nextDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            }

            // ── BUILD IMAGE ───────────────────────────────────────────────────
            if (!service.isDockerfileExists()) {
                dockerfileGenerator.generate(service);
            }

            updateStatus(deployment, DeploymentStatus.BUILDING_IMAGE);
            String imageName = dockerBuilder.build(service, deployment.getId());

            // ── PUSH IMAGE ────────────────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.PUSHING_IMAGE);

            String imageUri = dockerPushService.pushImage(
                    deployment.getAwsRoleArn(),
                    deployment.getAwsRegion(),
                    imageName
            );

            deployment.setImageUri(imageUri);
            deploymentRepository.save(deployment);

            // ── PROVISION INFRA ───────────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.PROVISIONING_INFRA);

            TerraformResult result = terraformService.provisionInfrastructure(
                    deployment.getAwsRoleArn(),
                    deployment.getAwsRegion(),
                    deployment.getExpectedUsers(),
                    80,
                    deployment.getId()
            );

            deployment.setEc2InstanceId(result.getInstanceId());
            deployment.setPublicIp(result.getPublicIp());
            deploymentRepository.save(deployment);

            // Set accessUrl immediately so UI shows it during the cloud-init wait
            int assignedPort = portAllocator.allocatePort();
            deployment.setAssignedPort(assignedPort);

            String accessUrl = "http://" + result.getPublicIp() + basePath + "/";
            deployment.setAccessUrl(accessUrl);
            deploymentRepository.save(deployment);

            System.out.println("Access URL: " + accessUrl);

            // Wait for cloud-init to finish installing docker/nginx/SSM on fresh EC2.
            // waitForSSM inside deployContainer handles SSM readiness, but SSM agent
            // can't even register until cloud-init finishes the apt installs first.
            System.out.println("Waiting 60s for EC2 cloud-init...");
            Thread.sleep(60_000);

            // ── DEPLOY CONTAINER ──────────────────────────────────────────────
            // deployContainer handles everything: wait for SSM, start docker,
            // ECR login, pull, run, AND health-check the container port.
            updateStatus(deployment, DeploymentStatus.DEPLOYING);

            ssmDeployService.deployContainer(
                    result.getInstanceId(),
                    deployment.getImageUri(),
                    assignedPort,
                    containerPort,
                    deployment.getAwsRegion(),
                    deployment.getAwsRoleArn(),
                    deployment.getId()
            );

            // FIX 4: verifyContainerViaSSM removed — it was redundant (deployContainer
            // already health-checks) and used curl -sf which fails on Next.js 404
            // responses when basePath is set. Both issues are fixed in deployContainer.

            // ── MARK RUNNING ──────────────────────────────────────────────────
            // Set RUNNING before the nginx query so this deployment is included
            // in findByStatusAndEc2InstanceId (fixes the missing-self bug).
            updateStatus(deployment, DeploymentStatus.RUNNING);

            // ── UPDATE NGINX ──────────────────────────────────────────────────
            List<Deployment> allRunning = deploymentRepository.findByStatusAndEc2InstanceId(
                    DeploymentStatus.RUNNING.name(),
                    result.getInstanceId()
            );

            // Defensive: add self if DB hasn't fully committed the status update yet
            if (allRunning.stream().noneMatch(d -> d.getId().equals(deployment.getId()))) {
                allRunning.add(deployment);
            }

            String nginxConfig = nginxConfigService.generateConfig(allRunning);

            ssmDeployService.updateNginx(
                    result.getInstanceId(),
                    nginxConfig,
                    deployment.getAwsRegion(),
                    deployment.getAwsRoleArn()
            );

            System.out.println("Deployment SUCCESS -> " + accessUrl);

        } catch (Exception e) {

            deployment.setStatus(DeploymentStatus.FAILED.name());
            // NPE and similar exceptions have null getMessage() — guard against that
            deployment.setLogs(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            deploymentRepository.save(deployment);
            e.printStackTrace();

        } finally {
            cleanup(workspace);
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private void updateStatus(Deployment deployment, DeploymentStatus status) {
        deployment.setStatus(status.name());
        deploymentRepository.save(deployment);
    }

    private void cloneRepo(Deployment deployment, Path workspace) throws Exception {
        String command = "git clone --depth=1 -b "
                + shellEscape(deployment.getBranch())
                + " "
                + shellEscape(deployment.getRepoUrl())
                + " "
                + workspace;

        Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});

        if (process.waitFor() != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes());
            throw new RuntimeException("Git clone failed: " + stderr);
        }
    }

    private String shellEscape(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void cleanup(Path workspace) {
        try {
            if (workspace == null) return;
            Files.walk(workspace)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {}
    }
}