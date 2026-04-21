package com.autopilot.service.deployment;

import com.autopilot.analyzer.RepoAnalyzerService;
import com.autopilot.analyzer.model.RepoAnalysisResult;
import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.dto.TerraformResult;
import com.autopilot.entity.Deployment;
import com.autopilot.enums.DeploymentStatus;
import com.autopilot.intelligence.ConfigIntelligencePipeline;
import com.autopilot.intelligence.model.ConfigEntry;
import com.autopilot.intelligence.model.ConfigIntelligenceResult;
import com.autopilot.repository.DeploymentRepository;
import com.autopilot.service.PortAllocatorService;
import com.autopilot.service.aws.DockerPushService;
import com.autopilot.service.aws.RdsProvisioningService;
import com.autopilot.service.aws.SecretsManagerService;
import com.autopilot.service.infrastructure.NginxConfigService;
import com.autopilot.service.infrastructure.ec2.SSMDeployService;
import com.autopilot.service.log.DeploymentLogService;
import com.autopilot.service.terraform.TerraformService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Self-healing deployment pipeline with 3-attempt retry system.
 *
 * On build failure, the pipeline:
 * 1. Captures Docker build logs
 * 2. Classifies the error (VERSION_MISMATCH, DEPENDENCY_ERROR, etc.)
 * 3. Applies an automatic fix to the ServiceConfig
 * 4. Regenerates the Dockerfile
 * 5. Retries the build (up to 3 attempts)
 */
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
    private final DeploymentLogService logService;
    private final ConfigIntelligencePipeline configIntelligence;
    private final SecretsManagerService secretsManagerService;
    private final RdsProvisioningService rdsProvisioningService;
    private final DependencyProvisionService dependencyProvisionService;
    private final HealthCheckService healthCheckService;
    private final StartupResilienceService startupResilienceService;

    private static final String WORKSPACE_ROOT = "/tmp/autopilot-workspaces";
    private static final int MAX_BUILD_ATTEMPTS = 3;

    public void execute(Deployment deployment) {

        Path workspace = null;
        String did = deployment.getId();

        try {

            // ── CLONE ─────────────────────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.CLONING);
            logService.info(did, "CLONING", "📥 Starting git clone for " + deployment.getRepoUrl() + " [branch: " + deployment.getBranch() + "]");
            logService.info(did, "CLONING", "Preparing workspace directory...");

            Files.createDirectories(Path.of(WORKSPACE_ROOT));
            workspace = Path.of(WORKSPACE_ROOT, UUID.randomUUID().toString());
            Files.createDirectories(workspace);

            cloneRepo(deployment, workspace);
            logService.info(did, "CLONING", "✅ Repository cloned successfully");

            // ── ANALYZE ───────────────────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.ANALYZING);
            logService.info(did, "ANALYZING", "🔍 Running 4-tier detection (Dockerfile → Template → AI → Fallback)...");
            logService.info(did, "ANALYZING", "Scanning project structure, package files, and build configs...");

            RepoAnalysisResult analysis = repoAnalyzerService.analyzeWorkspace(workspace);

            ServiceConfig service = null;
            ServiceConfig backendService = null;
            if (analysis.isMonoRepo() && analysis.getServices().size() > 1) {
                for (ServiceConfig s : analysis.getServices()) {
                    if ("javascript".equals(s.getLanguage())) service = s; // Frontend
                    else backendService = s; // Backend
                }
                if (service == null) {
                    service = analysis.getServices().get(0);
                    backendService = analysis.getServices().get(1);
                }
            } else {
                service = analysis.getServices().get(0);
            }

            // Persist analysis results
            deployment.setStrategyUsed(service.getStrategyUsed());
            deployment.setBuildCommand(service.getBuildCommand());
            deployment.setStartCommand(service.getStartCommand());
            deployment.setRuntimeVersion(service.getRuntimeVersion());
            deploymentRepository.save(deployment);

            logService.info(did, "ANALYZING", "✅ Strategy: " + service.getStrategyUsed()
                    + " | Framework: " + service.getFramework()
                    + " | Runtime: " + service.getRuntimeVersion());
            logService.info(did, "ANALYZING", "Build: " + (service.getBuildCommand() != null ? service.getBuildCommand() : "auto-detect")
                    + " | Start: " + (service.getStartCommand() != null ? service.getStartCommand() : "auto-detect")
                    + " | Port: " + (service.getPort() != null ? service.getPort() : "auto-detect"));

            // Resolve service path
            Path servicePath = workspace.resolve(service.getPath()).toAbsolutePath().normalize();
            if (Files.isRegularFile(servicePath)) {
                servicePath = servicePath.getParent();
            }
            service.setPath(servicePath.toString());

            if (backendService != null) {
                Path backendPath = workspace.resolve(backendService.getPath()).toAbsolutePath().normalize();
                if (Files.isRegularFile(backendPath)) {
                    backendPath = backendPath.getParent();
                }
                backendService.setPath(backendPath.toString());
            }

            int containerPort = service.getPort() != null ? service.getPort() : 3000;
            deployment.setPort(containerPort);

            String basePath = "/app-" + deployment.getId().replace("-", "").substring(0, 8);
            deployment.setBasePath(basePath);
            deploymentRepository.save(deployment);

            // Frontend patching
            String backendBaseUrlForPatcher = basePath + "-api";
            frontendPatcherService.patchFrontend(servicePath, basePath, backendBaseUrlForPatcher);

            // Clean stale build caches
            Path nextDir = servicePath.resolve(".next");
            if (Files.exists(nextDir)) {
                logService.info(did, "ANALYZING", "Cleaning stale .next cache");
                Files.walk(nextDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            }

            // ── CONFIG INTELLIGENCE ───────────────────────────────────────
            logService.info(did, "ANALYZING", "🧠 Running Config Intelligence Pipeline...");
            logService.info(did, "ANALYZING", "Scanning for environment variables, secrets, databases, and caches...");
            ConfigIntelligenceResult configResult = configIntelligence.analyze(workspace);

            long secretCount = configResult.getEntries().stream().filter(ConfigEntry::isSecret).count();
            logService.info(did, "ANALYZING", "Config Intelligence: "
                    + secretCount + " secrets, "
                    + configResult.getDatabases().size() + " databases, "
                    + configResult.getCaches().size() + " caches, "
                    + configResult.getEnvMap().size() + " env vars");

            // Persist config intelligence results
            deployment.setSecretCount((int) secretCount);
            deployment.setEnvVarCount(configResult.getEnvMap().size());
            deployment.setDetectedDatabases(String.join(",", configResult.getDatabases()));
            deployment.setDetectedCaches(String.join(",", configResult.getCaches()));
            deploymentRepository.save(deployment);

            if (!configResult.getSanitizedFiles().isEmpty()) {
                logService.info(did, "ANALYZING", "Sanitized " + configResult.getSanitizedFiles().size() + " files");
            }

            // ── DEPENDENCY PROVISIONING (DB, Redis, Secrets, Env Vars) ────
            // Inject custom user environment variables if present
            if (deployment.getCustomEnvVarsJson() != null && !deployment.getCustomEnvVarsJson().isBlank()) {
                logService.info(did, "ANALYZING", "🔑 Processing custom user environment variables...");
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.Map<String, String> customVars = mapper.readValue(deployment.getCustomEnvVarsJson(), 
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                    configResult.getEnvMap().putAll(customVars);
                    logService.info(did, "ANALYZING", "✅ Injected " + customVars.size() + " custom environment variables");
                } catch (Exception e) {
                    logService.info(did, "ANALYZING", "⚠️ Failed to parse custom user environment variables");
                }
            }

            // ── FRAMEWORK-AWARE DEFAULT ENV VARS ─────────────────────────────
            // Inject smart defaults based on detected framework to prevent common
            // deployment failures (e.g. test beans, wrong profiles, missing region)
            String framework = service.getFramework() != null ? service.getFramework().toLowerCase() : "";
            String backendFramework = backendService != null && backendService.getFramework() != null ? backendService.getFramework().toLowerCase() : "";
            String bothFrameworks = framework + " " + backendFramework;

            // Always inject AWS_REGION matching deployment target
            configResult.getEnvMap().putIfAbsent("AWS_REGION", deployment.getAwsRegion());
            configResult.getEnvMap().putIfAbsent("AWS_DEFAULT_REGION", deployment.getAwsRegion());

            if (bothFrameworks.contains("spring") || bothFrameworks.contains("boot")) {
                // Force production profile — prevents @PostConstruct test beans and  
                // dev-only components from crashing the app in production
                configResult.getEnvMap().putIfAbsent("SPRING_PROFILES_ACTIVE", "prod");
                configResult.getEnvMap().putIfAbsent("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
                configResult.getEnvMap().putIfAbsent("SERVER_PORT", String.valueOf(containerPort));

                // Force AWS SDK to use EC2 instance profile credentials (IMDS) instead of 
                // any hardcoded IAM user credentials in application.properties.
                // This prevents AccessDeniedException when hardcoded creds don't have the 
                // right permissions (e.g., S3, SQS, etc.)
                // Spring Boot relaxed binding: SPRING_CLOUD_AWS_CREDENTIALS_INSTANCE_PROFILE → spring.cloud.aws.credentials.instance-profile
                configResult.getEnvMap().put("SPRING_CLOUD_AWS_CREDENTIALS_INSTANCE_PROFILE", "true");
                configResult.getEnvMap().put("SPRING_CLOUD_AWS_CREDENTIALS_USE_DEFAULT_AWS_CREDENTIALS_CHAIN", "true");
                configResult.getEnvMap().put("SPRING_CLOUD_AWS_REGION_STATIC", deployment.getAwsRegion());
                // Mark hardcoded AWS keys as "placeholder" — the Docker env flag builder 
                // filters out values containing "placeholder", so these WON'T be injected.
                // With no explicit keys in env, the AWS SDK Default Credential Chain falls
                // through to EC2 instance metadata (IMDS) → uses the instance role.
                configResult.getEnvMap().put("CLOUD_AWS_CREDENTIALS_ACCESS_KEY", "placeholder-use-instance-role");
                configResult.getEnvMap().put("CLOUD_AWS_CREDENTIALS_SECRET_KEY", "placeholder-use-instance-role");
                configResult.getEnvMap().put("AWS_ACCESS_KEY_ID", "placeholder-use-instance-role");
                configResult.getEnvMap().put("AWS_SECRET_ACCESS_KEY", "placeholder-use-instance-role");

                logService.info(did, "ANALYZING", "✅ Injected Spring Boot production defaults (profile=prod, AWS=instance-role)");
            }
            if (bothFrameworks.contains("django")) {
                configResult.getEnvMap().putIfAbsent("DJANGO_SETTINGS_MODULE", "config.settings.production");
                configResult.getEnvMap().putIfAbsent("DEBUG", "false");
            }
            if (bothFrameworks.contains("node") || bothFrameworks.contains("next") || bothFrameworks.contains("express") || bothFrameworks.contains("react")) {
                configResult.getEnvMap().putIfAbsent("NODE_ENV", "production");
            }
            if (bothFrameworks.contains("flask") || bothFrameworks.contains("fastapi")) {
                configResult.getEnvMap().putIfAbsent("FLASK_ENV", "production");
                configResult.getEnvMap().putIfAbsent("PYTHONUNBUFFERED", "1");
            }
            if (bothFrameworks.contains("go") || bothFrameworks.contains("gin")) {
                configResult.getEnvMap().putIfAbsent("GIN_MODE", "release");
            }

            logService.info(did, "PROVISIONING_INFRA", "Running Dependency Provision + Auto-Link...");

            // Progress callback so RDS provisioning logs appear in real-time on frontend
            java.util.function.Consumer<String> progressLog = msg ->
                    logService.info(did, "PROVISIONING_INFRA", msg);

            DependencyProvisionService.ProvisionResult provisionResult =
                    dependencyProvisionService.provision(
                            configResult, did,
                            deployment.getAwsRoleArn(), deployment.getAwsRegion(),
                            workspace, null, progressLog // ec2InstanceId not yet known
                    );

            // Persist provision results
            if (provisionResult.rdsEndpoint() != null) {
                deployment.setRdsEndpoint(provisionResult.rdsEndpoint());
            }
            if (provisionResult.secretsArn() != null) {
                deployment.setSecretsArn(provisionResult.secretsArn());
            }
            deployment.setSecretCount((int) configResult.getEntries().stream()
                    .filter(ConfigEntry::isSecret).count());
            deployment.setEnvVarCount(provisionResult.envVars().size());
            deployment.setDetectedDatabases(String.join(",", configResult.getDatabases()));
            deployment.setDetectedCaches(String.join(",", configResult.getCaches()));
            deploymentRepository.save(deployment);

            // Log provisioned services
            for (String svc : provisionResult.provisionedServices()) {
                logService.info(did, "PROVISIONING_INFRA", "✅ Provisioned: " + svc);
            }
            for (String warn : provisionResult.warnings()) {
                logService.info(did, "PROVISIONING_INFRA", "⚠️ " + warn);
            }

            logService.info(did, "PROVISIONING_INFRA",
                    "📦 Final config: " + provisionResult.envVars().size() + " env vars, "
                    + provisionResult.dockerEnvFlags().size() + " docker flags ready for injection");

            // ── STARTUP RESILIENCE PATCHING ───────────────────────────────────
            // Scan for @PostConstruct methods that call external services (S3, SQS, etc.)
            // and wrap them in try-catch to prevent BeanCreationException crashes.
            // This fixes the common pattern where test/verification beans crash the
            // entire app due to IAM permission mismatches in production.
            if (bothFrameworks.contains("spring") || bothFrameworks.contains("boot")) {
                try {
                    logService.info(did, "ANALYZING", "🛡️ Scanning for dangerous @PostConstruct methods...");
                    int patchedFiles = startupResilienceService.patchDangerousInitMethods(
                            workspace, msg -> logService.info(did, "ANALYZING", msg));
                    if (patchedFiles > 0) {
                        logService.info(did, "ANALYZING", "🛡️ Patched " + patchedFiles
                                + " @PostConstruct method(s) for startup resilience");
                    }
                } catch (Exception e) {
                    logService.info(did, "ANALYZING", "⚠️ Startup resilience scan skipped: " + e.getMessage());
                }
            }

            // ── BUILD IMAGE (SELF-HEALING LOOP) ──────────────────────────────
            updateStatus(deployment, DeploymentStatus.BUILDING_IMAGE);
            logService.info(did, "BUILDING_IMAGE", "🔨 Starting Docker image build (self-healing, up to " + MAX_BUILD_ATTEMPTS + " attempts)...");
            String imageName = selfHealingBuild(deployment, service, did, "-frontend");

            String backendImageName = null;
            if (backendService != null) {
                logService.info(did, "BUILDING_IMAGE", "🔨 Building Backend Container Image...");
                backendImageName = selfHealingBuild(deployment, backendService, did, "-backend");
            }

            // ── PUSH IMAGE ────────────────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.PUSHING_IMAGE);
            logService.info(did, "PUSHING_IMAGE", "📤 Pushing Docker image to AWS ECR (" + deployment.getAwsRegion() + ")...");
            logService.info(did, "PUSHING_IMAGE", "This may take 1-3 minutes depending on image size...");

            String imageUri = dockerPushService.pushImage(
                    deployment.getAwsRoleArn(),
                    deployment.getAwsRegion(),
                    imageName
            );

            deployment.setImageUri(imageUri);
            
            String backendImageUri = null;
            if (backendImageName != null) {
                 backendImageUri = dockerPushService.pushImage(
                         deployment.getAwsRoleArn(), deployment.getAwsRegion(), backendImageName
                 );
            }
            
            deploymentRepository.save(deployment);
            logService.info(did, "PUSHING_IMAGE", "✅ Image pushed: " + imageUri);

            // ── PROVISION INFRA ───────────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.PROVISIONING_INFRA);
            logService.info(did, "PROVISIONING_INFRA", "🏗️ Provisioning AWS infrastructure...");
            logService.info(did, "PROVISIONING_INFRA", "Running Terraform init + apply (creating EC2 instance, security groups, IAM roles)...");
            logService.info(did, "PROVISIONING_INFRA", "This typically takes 1-2 minutes...");

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

            logService.info(did, "PROVISIONING_INFRA", "✅ EC2 created: " + result.getInstanceId() + " | Public IP: " + result.getPublicIp());

            // ── VALIDATE DEPENDENCY CONNECTIONS ──────────────────────────────
            if (provisionResult.rdsEndpoint() != null) {
                String ep = provisionResult.rdsEndpoint();
                // Docker-on-EC2 endpoints (autopilot-mysql, autopilot-postgres) are only
                // reachable from inside the EC2's Docker network — skip validation for those.
                boolean isDockerFallback = ep.startsWith("autopilot-mysql") || ep.startsWith("autopilot-postgres");
                boolean isRealRds = ep.contains(".rds.amazonaws.com");
                if (isDockerFallback) {
                    logService.info(did, "PROVISIONING_INFRA",
                            "⏩ DB validation skipped (Docker-on-EC2 — accessible only after deploy)");
                } else if (isRealRds) {
                    logService.info(did, "PROVISIONING_INFRA",
                            "✅ RDS endpoint: " + ep + " (VPC-internal — app will connect from EC2)");
                } else {
                    logService.info(did, "PROVISIONING_INFRA", "🔗 Validating DB connection to " + ep + "...");
                    boolean rdsOk = dependencyProvisionService.validateConnections(ep, null);
                    if (rdsOk) {
                        logService.info(did, "PROVISIONING_INFRA", "✅ DB connection validated");
                    } else {
                        logService.info(did, "PROVISIONING_INFRA", "⚠️ DB not reachable externally — app will attempt connection from EC2");
                    }
                }
            }

            int assignedPort = portAllocator.allocatePort();
            deployment.setAssignedPort(assignedPort);
            String accessUrl = "http://" + result.getPublicIp() + basePath + "/";
            deployment.setAccessUrl(accessUrl);
            deploymentRepository.save(deployment); // Immediately save to reserve the frontend port
            
            Integer backendPort = null;
            String backendBaseUrl = null;
            
            if (backendService != null) {
                 backendPort = portAllocator.allocatePort(); // Will now see the frontend port in DB and increment
                 backendBaseUrl = "http://" + result.getPublicIp() + basePath + "-api/";
                 
                 // Immediately save dummy deployment to reserve the backend port
                 Deployment bd = new Deployment();
                 bd.setId(java.util.UUID.randomUUID().toString());
                 bd.setEc2InstanceId(result.getInstanceId());
                 bd.setStatus(DeploymentStatus.RUNNING.name());
                 bd.setAssignedPort(backendPort);
                 bd.setBasePath(basePath + "-api");
                 deploymentRepository.save(bd);
            }

            logService.info(did, "PROVISIONING_INFRA", "🌐 Access URL: " + accessUrl);
            logService.info(did, "PROVISIONING_INFRA", "⏳ Waiting 60s for EC2 cloud-init to finish (installing Docker, Nginx, SSM agent)...");
            for (int waitSec = 0; waitSec < 60; waitSec += 15) {
                Thread.sleep(15_000);
                int elapsed = waitSec + 15;
                logService.info(did, "PROVISIONING_INFRA", "⏳ Cloud-init: " + elapsed + "/60s elapsed...");
            }

            // ── DEPLOY CONTAINER (WITH ENV INJECTION) ────────────────────────
            updateStatus(deployment, DeploymentStatus.DEPLOYING);
            logService.info(did, "DEPLOYING", "🚀 Starting container deployment on EC2...");
            logService.info(did, "DEPLOYING", "Injecting " + provisionResult.dockerEnvFlags().size() + " environment variables into container...");

            // Build pre-deploy commands (e.g., Redis container, Database fallbacks)
            List<String> preDeployCommands = new java.util.ArrayList<>();
            if (configResult.getCaches().stream().anyMatch(c -> c.equalsIgnoreCase("redis"))) {
                logService.info(did, "DEPLOYING", "🔴 Adding Redis container to deployment...");
                preDeployCommands.addAll(dependencyProvisionService.buildRedisProvisionCommands());
            }
            if (provisionResult.preDeployDbCommands() != null && !provisionResult.preDeployDbCommands().isEmpty()) {
                logService.info(did, "DEPLOYING", "🗄️ Adding local database container to deployment...");
                preDeployCommands.addAll(provisionResult.preDeployDbCommands());
            }
            
            if (backendService != null && backendImageUri != null) {
                // Inject backend urls into frontend env
                provisionResult.dockerEnvFlags().add("-e NEXT_PUBLIC_API_URL='" + backendBaseUrl + "'");
                provisionResult.dockerEnvFlags().add("-e REACT_APP_API_URL='" + backendBaseUrl + "'");
                provisionResult.dockerEnvFlags().add("-e VITE_API_BASE_URL='" + backendBaseUrl + "'");
                provisionResult.dockerEnvFlags().add("-e BACKEND_URL='http://127.0.0.1:" + backendPort + "'");
                 
                String backendContainer = "autopilot-backend-" + did;
                int targetPort = backendService.getPort() != null ? backendService.getPort() : 8080;
                 
                StringBuilder br = new StringBuilder("docker run -d --name " + backendContainer + " --network autopilot --restart unless-stopped -p 127.0.0.1:" + backendPort + ":" + targetPort);
                for (String f : provisionResult.dockerEnvFlags()) {
                    br.append(" ").append(f);
                }
                br.append(" -e SERVER_PORT=").append(targetPort);
                br.append(" -e PORT=").append(targetPort);
                br.append(" ").append(backendImageUri);
                 
                preDeployCommands.add("docker pull " + backendImageUri);
                preDeployCommands.add("docker rm -f " + backendContainer + " 2>/dev/null || true");
                preDeployCommands.add(br.toString());
                 
                logService.info(did, "DEPLOYING", "✅ Synchronizing Backend Container at " + backendBaseUrl);
            }

            logService.info(did, "DEPLOYING", "📡 Sending SSM deploy command to " + result.getInstanceId() + "...");
            logService.info(did, "DEPLOYING", "Steps: ECR login → pull image → create network → start containers → health check");
            logService.info(did, "DEPLOYING", "⏳ This takes 3-8 minutes. Please wait...");

            // Progress callback for SSM operations
            java.util.function.Consumer<String> deployLog = msg ->
                    logService.info(did, "DEPLOYING", msg);

            ssmDeployService.deployContainer(
                    result.getInstanceId(),
                    deployment.getImageUri(),
                    assignedPort,
                    containerPort,
                    deployment.getAwsRegion(),
                    deployment.getAwsRoleArn(),
                    deployment.getId(),
                    provisionResult.dockerEnvFlags(),
                    preDeployCommands,
                    deployLog
            );

            logService.info(did, "DEPLOYING", "✅ All containers started successfully on EC2");

            // ── UPDATE NGINX ──────────────────────────────────────────────────
            logService.info(did, "DEPLOYING", "🔧 Configuring Nginx reverse proxy...");
            List<Deployment> allRunning = deploymentRepository.findByStatusAndEc2InstanceId(
                    DeploymentStatus.RUNNING.name(),
                    result.getInstanceId()
            );

            if (allRunning.stream().noneMatch(d -> d.getId().equals(deployment.getId()))) {
                allRunning.add(deployment);
            }

            String nginxConfig = nginxConfigService.generateConfig(allRunning);
            logService.info(did, "DEPLOYING", "Uploading Nginx config and reloading...");

            ssmDeployService.updateNginx(
                    result.getInstanceId(),
                    nginxConfig,
                    deployment.getAwsRegion(),
                    deployment.getAwsRoleArn()
            );

            logService.info(did, "DEPLOYING", "✅ Nginx configured and running");

            // ── POST-DEPLOY HEALTH VALIDATION ────────────────────────────────
            logService.info(did, "DEPLOYING", "🏥 Running post-deploy health validation...");
            logService.info(did, "DEPLOYING", "Checking HTTP connectivity to " + accessUrl + "...");

            HealthCheckService.HealthResult healthResult = healthCheckService.validate(
                    deployment, result.getPublicIp(), basePath);

            if (!healthResult.healthy()) {
                String diagnosis = healthCheckService.classifyFailure(healthResult);
                logService.error(did, "DEPLOYING", "❌ Health check failed: " + diagnosis);
                throw new RuntimeException("Post-deploy validation failed: " + diagnosis);
            }

            logService.info(did, "DEPLOYING", "✅ Application responding — HTTP " + healthResult.httpStatus()
                    + " (" + healthResult.responseTimeMs() + "ms)");

            logService.info(did, "SUCCESS", "🎉 Deployment complete!");
            logService.info(did, "SUCCESS", "🌐 Your app is live at: " + accessUrl);
            logService.complete(did);

        } catch (Exception e) {

            deployment.setStatus(DeploymentStatus.FAILED.name());
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            deployment.setLogs(errorMsg);
            deploymentRepository.save(deployment);

            logService.error(did, "FAILED", "❌ Deployment failed: " + errorMsg);
            logService.complete(did);

            e.printStackTrace();

        } finally {
            cleanup(workspace);
            terraformService.cleanupWorkspace(deployment.getId());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SELF-HEALING BUILD ENGINE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Attempts to build the Docker image up to MAX_BUILD_ATTEMPTS times.
     * After each failure, classifies the error and applies a targeted fix.
     */
    private String selfHealingBuild(Deployment deployment, ServiceConfig service, String did, String suffix) throws Exception {

        for (int attempt = 1; attempt <= MAX_BUILD_ATTEMPTS; attempt++) {

            logService.info(did, "BUILDING_IMAGE", "── Build Attempt " + attempt + "/" + MAX_BUILD_ATTEMPTS
                    + " [strategy: " + service.getStrategyUsed() + "] ──");

            // Generate Dockerfile
            try {
                dockerfileGenerator.generate(service);
            } catch (Exception genError) {
                logService.error(did, "BUILDING_IMAGE", "Dockerfile generation failed: " + genError.getMessage());
                applyFallbackFix(service, "GENERATION_ERROR");
                continue;
            }

            // Run docker build
            DockerBuilder.BuildResult result = dockerBuilder.buildSafeSuffix(service, deployment.getId(), suffix);

            if (result.success) {
                logService.info(did, "BUILDING_IMAGE", "✅ Build succeeded on attempt " + attempt + ": " + result.imageName);
                return result.imageName;
            }

            // Build failed — classify error and apply fix
            logService.error(did, "BUILDING_IMAGE", "⚠️ Build failed — error category: " + result.errorCategory);
            logService.info(did, "BUILDING_IMAGE", "Applying self-healing fix for: " + result.errorCategory);

            applySelfHealingFix(service, result.errorCategory, result.logs);

            // Force Dockerfile regeneration on next attempt
            service.setDockerfileExists(false);

            // Delete the broken Dockerfile
            try {
                Path brokenDockerfile = Path.of(service.getPath()).resolve("Dockerfile");
                Files.deleteIfExists(brokenDockerfile);
            } catch (Exception ignored) {}
        }

        throw new RuntimeException("All " + MAX_BUILD_ATTEMPTS + " build attempts failed. Last strategy: " + service.getStrategyUsed());
    }

    /**
     * Apply a targeted fix based on the classified build error.
     */
    private void applySelfHealingFix(ServiceConfig service, String errorCategory, List<String> logs) {

        switch (errorCategory) {

            case "VERSION_MISMATCH" -> {
                // The build JDK doesn't match the project's required version.
                // Try extracting the version from the error message itself.
                String detectedVersion = extractVersionFromLogs(logs);
                if (detectedVersion != null) {
                    System.out.println("🔧 Self-heal: Switching runtime from "
                            + service.getRuntimeVersion() + " → " + detectedVersion);
                    service.setRuntimeVersion(detectedVersion);
                } else {
                    // Toggle between common Java versions
                    String current = service.getRuntimeVersion();
                    if ("21".equals(current)) {
                        service.setRuntimeVersion("17");
                    } else if ("17".equals(current)) {
                        service.setRuntimeVersion("21");
                    } else {
                        service.setRuntimeVersion("17"); // safest
                    }
                }
                service.setStrategyUsed("SELF_HEALED");
            }

            case "DEPENDENCY_ERROR", "NPM_ERROR", "PIP_ERROR" -> {
                // Dependency resolution failed — try without strict mode
                String buildCmd = service.getBuildCommand();
                if (buildCmd != null && buildCmd.contains("npm install")) {
                    service.setBuildCommand("npm install --legacy-peer-deps");
                } else if (buildCmd != null && buildCmd.contains("pip install")) {
                    service.setBuildCommand(buildCmd + " || true");
                }
                service.setStrategyUsed("SELF_HEALED");
            }

            case "PERMISSION_ERROR" -> {
                // Most common cause: mvnw/gradlew missing execute permission after git clone.
                // Fix: Prepend chmod +x to the build command so wrappers become executable.
                String permBuildCmd = service.getBuildCommand();
                if (permBuildCmd != null && (permBuildCmd.contains("mvnw") || permBuildCmd.contains("gradlew"))) {
                    String wrapper = permBuildCmd.contains("mvnw") ? "mvnw" : "gradlew";
                    System.out.println("🔧 Self-heal: Adding chmod +x " + wrapper + " before build");
                    service.setBuildCommand("chmod +x " + wrapper + " && " + permBuildCmd);
                } else {
                    // Generic permission fix — try running with sh
                    System.out.println("🔧 Self-heal: Wrapping build command with sh -c");
                    if (permBuildCmd != null) {
                        service.setBuildCommand("sh -c '" + permBuildCmd.replace("'", "'\\''" ) + "'");
                    }
                }
                service.setStrategyUsed("SELF_HEALED");
            }

            case "FILE_NOT_FOUND" -> {
                // A file that the Dockerfile expects doesn't exist.
                // Switch to universal fallback that handles missing files gracefully.
                applyFallbackFix(service, errorCategory);
            }

            default -> {
                // Unknown error — go to full universal fallback
                applyFallbackFix(service, errorCategory);
            }
        }
    }

    /**
     * Nuclear option: switch to a universal fallback Dockerfile strategy.
     */
    private void applyFallbackFix(ServiceConfig service, String reason) {
        System.out.println("🛡️ Applying FALLBACK fix (reason: " + reason + ")");
        service.setStrategyUsed("FALLBACK");
        service.setFramework("generic");
        // Keep original language/buildCommand so the fallback generator picks the right base image
    }

    /**
     * Try to extract the required Java version from Maven error output.
     * Looks for patterns like "release version 21 not supported".
     */
    private String extractVersionFromLogs(List<String> logs) {
        for (String line : logs) {
            // "error: release version 21 not supported"
            if (line.contains("release version") && line.contains("not supported")) {
                // The version in the error is what the PROJECT wants.
                // We need to give it that version.
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("release version (\\d+)")
                        .matcher(line);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private void updateStatus(Deployment deployment, DeploymentStatus status) {
        deployment.setStatus(status.name());
        deploymentRepository.save(deployment);
    }

    private void cloneRepo(Deployment deployment, Path workspace) throws Exception {
        String branch = deployment.getBranch();
        String repoUrl = deployment.getRepoUrl();

        // Attempt 1: Clone specific branch
        String command = "git clone --depth=1 -b "
                + shellEscape(branch)
                + " "
                + shellEscape(repoUrl)
                + " "
                + workspace;

        Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});

        if (process.waitFor() != 0) {
            // Attempt 2: Clone default branch
            logService.info(deployment.getId(), "CLONING",
                    "Branch '" + branch + "' not found → trying default branch");

            // Clean workspace for fresh clone
            cleanup(workspace);
            Files.createDirectories(workspace);

            String fallbackCommand = "git clone --depth=1 "
                    + shellEscape(repoUrl)
                    + " "
                    + workspace;

            Process fallbackProcess = Runtime.getRuntime().exec(new String[]{"bash", "-c", fallbackCommand});

            if (fallbackProcess.waitFor() != 0) {
                String fallbackStderr = new String(fallbackProcess.getErrorStream().readAllBytes());
                throw new RuntimeException("Git clone failed: " + fallbackStderr);
            }
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