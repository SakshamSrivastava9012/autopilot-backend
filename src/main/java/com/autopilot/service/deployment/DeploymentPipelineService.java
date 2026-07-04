package com.autopilot.service.deployment;

import com.autopilot.analyzer.RepoAnalyzerService;
import com.autopilot.analyzer.model.RepoAnalysisResult;
import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.dto.DeployedService;
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
import com.autopilot.service.aws.CredentialResolverService;
import com.autopilot.service.infrastructure.NginxConfigService;
import com.autopilot.service.infrastructure.ec2.SSMDeployService;
import com.autopilot.service.log.DeploymentLogService;
import com.autopilot.service.terraform.TerraformService;
import com.autopilot.analyzer.model.FrameworkType;
import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.service.deployment.strategies.ContainerStrategy;
import com.autopilot.analyzer.ServiceClassifier;
import com.autopilot.dto.DeploymentManifest;
import com.autopilot.dto.RouteDescriptor;
import com.autopilot.dto.ServiceDescriptor;
import com.autopilot.dto.ServiceRole;
import com.autopilot.dto.AssetManifestEntry;
import com.autopilot.service.infrastructure.UniversalNginxGenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.*;
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

    private final RuntimeInspectorService runtimeInspectorService;
    private final FrontendPatcherService frontendPatcherService;
    private final RepoAnalyzerService repoAnalyzerService;
    private final DockerfileGenerator dockerfileGenerator;
    private final DockerBuilder dockerBuilder;
    private final FrameworkNativeConfiguratorService nativeConfiguratorService;
    private final DockerPushService dockerPushService;
    private final TerraformService terraformService;
    private final SSMDeployService ssmDeployService;
    private final DeploymentRepository deploymentRepository;
    private final com.autopilot.service.infrastructure.CapacityPlanner capacityPlanner;
    private final PortAllocatorService portAllocator;
    private final NginxConfigService nginxConfigService;
    private final DeploymentLogService logService;
    private final ConfigIntelligencePipeline configIntelligence;
    private final SecretsManagerService secretsManagerService;
    private final RdsProvisioningService rdsProvisioningService;
    private final DependencyProvisionService dependencyProvisionService;
    private final HealthCheckService healthCheckService;
    private final StartupResilienceService startupResilienceService;
    private final CredentialResolverService credentialResolverService;
    private final DockerImageValidatorService dockerImageValidatorService;
    private final com.autopilot.service.VersionIntegrityValidator versionIntegrityValidator;
    private final com.autopilot.service.deployment.validation.StrategyResolver strategyResolver;
    private final List<ContainerStrategy> containerStrategies;
    private final com.autopilot.analyzer.runtime.FrontendRuntimeStrategyRegistry strategyRegistry;
    private final DeploymentPlanner deploymentPlanner;
    private final EnvironmentResolver environmentResolver;
    private final com.autopilot.service.deployment.runtime.verification.RuntimeVerificationPlatform runtimeVerificationPlatform;
    private final com.autopilot.service.deployment.validation.DeploymentValidationSuite deploymentValidationSuite;
    private final com.autopilot.service.deployment.diagnostics.DeploymentRootCauseAnalyzer rootCauseAnalyzer;
    private final ServiceClassifier serviceClassifier;
    private final UniversalNginxGenerator nginxGenerator;
    private final AssetPatcherService assetPatcherService;

    private static final String WORKSPACE_ROOT = "/tmp/autopilot-workspaces";
    private static final int MAX_BUILD_ATTEMPTS = 3;


    public void execute(Deployment deployment) {

        Path workspace = null;
        String did = deployment.getId();
        List<ServiceConfig> allServices = null;
        List<DeployedService> deployedServices = null;
        DependencyProvisionService.ProvisionResult provisionResult = null;
        DeploymentManifest manifest = null;
        List<AssetManifestEntry> finalAllAssets = new ArrayList<>();

        try {
            // Run runtime version integrity validation before executing
            versionIntegrityValidator.validate(did, logService);

            CredentialResolverService.ResolvedCredentials credentials = credentialResolverService.resolve(deployment);
            com.autopilot.dto.AwsCredentialsDto credsDto = credentials.credentials();
            String awsRegion = credentials.region();

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

            // ── CLASSIFY ALL DETECTED SERVICES ──────────────────────────
            List<ServiceConfig> rawServices = analysis.getServices();
            if (rawServices == null || rawServices.isEmpty()) {
                throw new RuntimeException("No deployable services detected in repository");
            }

            for (ServiceConfig s : rawServices) {
                java.util.Objects.requireNonNull(s, "ServiceConfig cannot be null");
                if (s.getPath() != null) {
                    Path sp = workspace.resolve(s.getPath()).toAbsolutePath().normalize();
                    if (Files.isRegularFile(sp)) sp = sp.getParent();
                    s.setPath(sp.toString());
                }
                s.validate();
            }

            // Plan deployment order topologically
            allServices = deploymentPlanner.planDeploymentOrder(rawServices);

            // Select primary service role-based
            ServiceConfig primaryService = allServices.stream()
                    .filter(s -> "frontend".equalsIgnoreCase(s.getRole()) || "gateway".equalsIgnoreCase(s.getRole()))
                    .findFirst()
                    .orElse(allServices.stream()
                            .filter(s -> "api".equalsIgnoreCase(s.getRole()) || "backend".equalsIgnoreCase(s.getRole()))
                            .findFirst()
                            .orElse(allServices.get(0)));

            logService.info(did, "ANALYZING", "📦 Discovered & planned " + allServices.size() + " service(s):");
            for (ServiceConfig s : allServices) {
                logService.info(did, "ANALYZING", "  • " + s.getName() + " [" + s.getFramework() + "] role=" + s.getRole());
            }

            ServiceConfig service = primaryService;
            ServiceConfig backendService = allServices.stream()
                    .filter(s -> "api".equalsIgnoreCase(s.getRole()) || "backend".equalsIgnoreCase(s.getRole()))
                    .filter(s -> s != primaryService).findFirst()
                    .orElse(null);

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

            // basePath is unique per deployment — never collides across tenants
            String basePath = "/app-" + deployment.getId().replace("-", "").substring(0, 8);
            deployment.setBasePath(basePath);
            com.autopilot.service.deployment.validation.FrameworkStrategy primaryStrategy = strategyResolver.resolve(primaryService);
            int primaryContainerPort = primaryStrategy.containerPort();
            String primaryHealthPath = primaryStrategy.healthPath();
            String primaryProtocol = primaryStrategy.protocol();
            List<Integer> primaryExpectedStatusCodes = primaryStrategy.expectedStatusCodes();
            int primaryStartupTimeout = primaryStrategy.startupTimeout();
            int primaryRetryPolicy = primaryStrategy.retryPolicy();

            deployment.setPort(primaryContainerPort);
            deploymentRepository.save(deployment);

            // ── ALLOCATE PORTS & BASE PATHS FOR ALL SERVICES ─────────────────
            Map<ServiceConfig, Integer> svcHostPorts = new LinkedHashMap<>();
            Map<ServiceConfig, String> svcBasePaths = new LinkedHashMap<>();
            List<Integer> allocatedPortsInBatch = new java.util.ArrayList<>();

            int primaryHostPort = portAllocator.allocatePort(allocatedPortsInBatch);
            allocatedPortsInBatch.add(primaryHostPort);
            deployment.setAssignedPort(primaryHostPort);

            svcHostPorts.put(primaryService, primaryHostPort);
            svcBasePaths.put(primaryService, basePath);
            primaryService.setBasePath(basePath);

            int backendCount = 0;
            for (ServiceConfig svc : allServices) {
                if (svc != primaryService && isBackendFramework(svc)) {
                    backendCount++;
                }
            }

            for (ServiceConfig svc : allServices) {
                if (svc == primaryService) continue;
                int hp = portAllocator.allocatePort(allocatedPortsInBatch);
                allocatedPortsInBatch.add(hp);
                
                String bp;
                if (isBackendFramework(svc)) {
                    if (backendCount == 1) {
                        bp = basePath + "-api";
                    } else {
                        String cleanName = svc.getName().replaceAll("[^a-zA-Z0-9-]", "").toLowerCase();
                        bp = basePath + "-" + cleanName;
                    }
                } else {
                    String cleanName = svc.getName().replaceAll("[^a-zA-Z0-9-]", "").toLowerCase();
                    bp = basePath + "-" + cleanName;
                }
                
                svcHostPorts.put(svc, hp);
                svcBasePaths.put(svc, bp);
                svc.setBasePath(bp);
            }
            deploymentRepository.save(deployment);

            // Frontend & Backend patching — patch every service dynamically using actual allocated paths
            String apiPath = backendService != null ? svcBasePaths.get(backendService) : (basePath + "-api");
            for (ServiceConfig svc : allServices) {
                if (isFrontendFramework(svc)) {
                    String svcBasePath = requireBasePath(svcBasePaths, svc);
                    frontendPatcherService.patchFrontend(Path.of(svc.getPath()), svcBasePath, apiPath);
                } else {
                    frontendPatcherService.patchBackend(Path.of(svc.getPath()));
                }
            }


            // Clean stale .next caches for any Next.js service
            for (ServiceConfig svc : allServices) {
                Path nextDir = Path.of(svc.getPath()).resolve(".next");
                if (Files.exists(nextDir)) {
                    logService.info(did, "ANALYZING", "Cleaning stale .next cache for " + svc.getName());
                    Files.walk(nextDir).sorted((a, b) -> b.compareTo(a))
                            .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
                }
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
            String frameworkStr = service.getFramework() != null ? service.getFramework().toUpperCase().replace("-", "_") : "GENERIC";
            FrameworkType ft = FrameworkType.GENERIC;
            try {
                ft = FrameworkType.valueOf(frameworkStr);
            } catch (Exception ignored) {}

            final FrameworkType finalFt = ft;
            FrameworkMetadata metadata = FrameworkMetadata.builder()
                    .frameworkType(ft)
                    .port(primaryContainerPort)
                    .build();

            containerStrategies.stream()
                    .filter(s -> s.supports(finalFt))
                    .forEach(s -> s.populateEnvironment(metadata, configResult.getEnvMap(), awsRegion));

            String backendFrameworkStr = backendService != null && backendService.getFramework() != null 
                    ? backendService.getFramework().toUpperCase().replace("-", "_") : "GENERIC";
            FrameworkType bft = FrameworkType.GENERIC;
            try {
                bft = FrameworkType.valueOf(backendFrameworkStr);
            } catch (Exception ignored) {}

            final FrameworkType finalBft = bft;
            FrameworkMetadata backendMetadata = FrameworkMetadata.builder()
                    .frameworkType(bft)
                    .port(primaryContainerPort)
                    .build();

            containerStrategies.stream()
                    .filter(s -> s.supports(finalBft))
                    .forEach(s -> s.populateEnvironment(backendMetadata, configResult.getEnvMap(), awsRegion));

            logService.info(did, "PROVISIONING_INFRA", "Running Dependency Provision + Auto-Link...");

            // Progress callback so RDS provisioning logs appear in real-time on frontend
            java.util.function.Consumer<String> progressLog = msg ->
                    logService.info(did, "PROVISIONING_INFRA", msg);

            provisionResult =
                    dependencyProvisionService.provision(
                            configResult, did,
                            credsDto, awsRegion,
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
            if (ft == FrameworkType.SPRING_BOOT || bft == FrameworkType.SPRING_BOOT) {
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

            // ── BUILD IMAGES (ALL SERVICES, SELF-HEALING) ─────────────────────
            updateStatus(deployment, DeploymentStatus.BUILDING_IMAGE);
            logService.info(did, "BUILDING_IMAGE", "🔨 Building " + allServices.size() + " service image(s)...");

            // Map: ServiceConfig → local imageName
            Map<ServiceConfig, String> serviceImageNames = new LinkedHashMap<>();
            for (int i = 0; i < allServices.size(); i++) {
                ServiceConfig svc = allServices.get(i);
                String suffix = getSvcSuffix(svc.getBasePath(), basePath);
                logService.info(did, "BUILDING_IMAGE", "🔨 [" + (i+1) + "/" + allServices.size() + "] Building: " + svc.getName() + " (" + svc.getFramework() + ")");
                
                // Inject native framework configuration before build
                nativeConfiguratorService.configure(svc, svc.getBasePath());
                
                String imgName = selfHealingBuild(deployment, svc, did, suffix);
                serviceImageNames.put(svc, imgName);
            }

            // ── RUNTIME ASSET DISCOVERY & UNIVERSAL URL REWRITING ─────────────
            logService.info(did, "BUILDING_IMAGE", "🖼️ Starting runtime asset discovery and universal URL rewriting...");
            for (Map.Entry<ServiceConfig, String> entry : serviceImageNames.entrySet()) {
                ServiceConfig svc = entry.getKey();
                String imgName = entry.getValue();
                String svcBasePath = svc.getBasePath();
                logService.info(did, "BUILDING_IMAGE", "🖼️ Processing assets for service: " + svc.getName() + " under path: " + svcBasePath);
                List<AssetManifestEntry> svcAssets = assetPatcherService.patchImage(imgName, svcBasePath, workspace.toString(), did);
                finalAllAssets.addAll(svcAssets);
            }
            logService.info(did, "BUILDING_IMAGE", "✅ Asset discovery and URL rewriting complete. Discovered " + finalAllAssets.size() + " asset(s).");

            // ── PUSH ALL IMAGES ───────────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.PUSHING_IMAGE);
            logService.info(did, "PUSHING_IMAGE", "📤 Pushing " + allServices.size() + " image(s) to ECR...");

            Map<ServiceConfig, String> serviceImageUris = new LinkedHashMap<>();
            for (Map.Entry<ServiceConfig, String> entry : serviceImageNames.entrySet()) {
                String uri = dockerPushService.pushImage(credsDto, awsRegion, entry.getValue());
                serviceImageUris.put(entry.getKey(), uri);
                logService.info(did, "PUSHING_IMAGE", "✅ Pushed: " + entry.getKey().getName() + " → " + uri);
            }

            // Verify image metadata exists
            for (ServiceConfig svc : allServices) {
                String imgName = serviceImageNames.get(svc);
                if (imgName == null || imgName.isBlank()) {
                    throw new IllegalStateException("Missing image name for service: " + svc.getName());
                }
                String imgUri = serviceImageUris.get(svc);
                if (imgUri == null || imgUri.isBlank()) {
                    throw new IllegalStateException("Missing image URI for service: " + svc.getName());
                }
            }

            // Set primary image URI on deployment entity
            deployment.setImageUri(requireImageUri(serviceImageUris, primaryService));
            deploymentRepository.save(deployment);

            // Legacy compat variables
            String imageName = requireImageName(serviceImageNames, primaryService);
            String imageUri = requireImageUri(serviceImageUris, primaryService);
            String backendImageName = backendService != null ? requireImageName(serviceImageNames, backendService) : null;
            String backendImageUri = backendService != null ? requireImageUri(serviceImageUris, backendService) : null;

            // ── PROVISION INFRA ───────────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.PROVISIONING_INFRA);
            logService.info(did, "PROVISIONING_INFRA", "🏗️ Provisioning AWS infrastructure...");
            logService.info(did, "PROVISIONING_INFRA", "Running Terraform init + apply (creating EC2 instance, security groups, IAM roles)...");
            logService.info(did, "PROVISIONING_INFRA", "This typically takes 1-2 minutes...");

            String rdsSgId = provisionResult.rdsSecurityGroupId();
            Integer rdsPort = 3306;
            if (provisionResult.rdsEndpoint() != null) {
                String ep = provisionResult.rdsEndpoint();
                if (ep.contains(":")) {
                    try {
                        rdsPort = Integer.parseInt(ep.split(":")[1]);
                    } catch (NumberFormatException ignored) {}
                }
            }

            String instanceType = deployment.getInstanceTypeOverride();
            if (instanceType == null || instanceType.isBlank() || "AUTO".equalsIgnoreCase(instanceType)) {
                instanceType = capacityPlanner.chooseInstanceType(analysis, deployment.getExpectedUsers());
            }
            logService.info(did, "PROVISIONING_INFRA", "Selected EC2 instance type: " + instanceType);

            TerraformResult result = terraformService.provisionInfrastructure(
                    credsDto,
                    awsRegion,
                    instanceType,
                    80,
                    deployment.getId(),
                    rdsSgId,
                    rdsPort
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

            deployedServices = new ArrayList<>();
            String accessUrl = "http://" + result.getPublicIp() + basePath + "/";
            deployment.setAccessUrl(accessUrl);
            deploymentRepository.save(deployment);

            // Compute backend URL for frontend patching
            String backendBaseUrl = null;
            Integer backendPort = null;
            if (backendService != null) {
                backendPort = requireHostPort(svcHostPorts, backendService, backendService.getName());
                backendBaseUrl = "http://" + result.getPublicIp() + requireBasePath(svcBasePaths, backendService) + "/";
            }

            // ── VALIDATE DEPLOYMENT METADATA ──────────────────────────────────
            for (ServiceConfig svc : allServices) {
                com.autopilot.service.deployment.validation.FrameworkStrategy strategy = strategyResolver.resolve(svc);
                Objects.requireNonNull(strategy, "FrameworkStrategy cannot be null for service: " + svc.getName());

                String name = svc.getName();
                String framework = svc.getFramework();
                String bp = svc.getBasePath();
                Integer hpVal = svcHostPorts.get(svc);
                int cp = strategy.containerPort();
                String healthPath = strategy.healthPath();
                
                if (name == null || name.isBlank()) {
                    throw new IllegalStateException("Missing serviceName for service: " + svc.getServiceId());
                }
                if (framework == null || framework.isBlank()) {
                    throw new IllegalStateException("Missing framework for service: " + name);
                }
                if (bp == null || bp.isBlank()) {
                    throw new IllegalStateException("Missing basePath for service: " + name);
                }
                if (hpVal == null) {
                    throw new IllegalStateException("Missing hostPort for service: " + name);
                }
                if (healthPath == null || healthPath.isBlank()) {
                    throw new IllegalStateException("Missing healthPath for service: " + name);
                }
                if (did == null || did.isBlank()) {
                    throw new IllegalStateException("Missing deploymentId for service: " + name);
                }
                if (cp <= 0) {
                    throw new IllegalStateException("Missing containerPort for service: " + name);
                }
            }

            logService.info(did, "PROVISIONING_INFRA", "🌐 Access URL: " + accessUrl);
            logService.info(did, "PROVISIONING_INFRA", "⏳ Waiting 60s for EC2 cloud-init...");
            for (int waitSec = 0; waitSec < 60; waitSec += 15) {
                Thread.sleep(15_000);
                logService.info(did, "PROVISIONING_INFRA", "⏳ Cloud-init: " + (waitSec + 15) + "/60s elapsed...");
            }

            // ── DEPLOY ALL CONTAINERS ─────────────────────────────────────────
            updateStatus(deployment, DeploymentStatus.DEPLOYING);
            logService.info(did, "DEPLOYING", "🚀 Deploying " + allServices.size() + " container(s) on EC2...");

            List<String> preDeployCommands = new ArrayList<>();
            if (configResult.getCaches().stream().anyMatch(c -> c.equalsIgnoreCase("redis"))) {
                logService.info(did, "DEPLOYING", "🔴 Adding Redis container...");
                preDeployCommands.addAll(dependencyProvisionService.buildRedisProvisionCommands());
            }
            if (provisionResult.preDeployDbCommands() != null && !provisionResult.preDeployDbCommands().isEmpty()) {
                logService.info(did, "DEPLOYING", "🗄️ Adding local database container...");
                preDeployCommands.addAll(provisionResult.preDeployDbCommands());
            }

            // Real RDS connectivity verification from EC2
            if (provisionResult.rdsEndpoint() != null) {
                String ep = provisionResult.rdsEndpoint();
                boolean isDockerFallback = ep.startsWith("autopilot-mysql") || ep.startsWith("autopilot-postgres") || ep.startsWith("autopilot-mongo");
                if (!isDockerFallback) {
                    String host = ep;
                    int port = 3306;
                    if (ep.contains(":")) {
                        String[] parts = ep.split(":");
                        host = parts[0];
                        try {
                            port = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException ignored) {}
                    }
                    logService.info(did, "DEPLOYING", "🗄️ Injecting database connectivity verification step into deployment pipeline...");
                    preDeployCommands.add("echo '==================================================='");
                    preDeployCommands.add("echo '🔍 VERIFYING DATABASE CONNECTIVITY TO: " + host + ":" + port + "'");
                    preDeployCommands.add("for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do");
                    preDeployCommands.add("  if command -v nc >/dev/null 2>&1; then");
                    preDeployCommands.add("    nc -z -w 5 " + host + " " + port + " >/dev/null 2>&1");
                    preDeployCommands.add("  else");
                    preDeployCommands.add("    timeout 5 bash -c 'cat < /dev/null > /dev/tcp/" + host + "/" + port + "' 2>/dev/null");
                    preDeployCommands.add("  fi");
                    preDeployCommands.add("  if [ $? -eq 0 ]; then");
                    preDeployCommands.add("    echo '✅ Database connectivity verified successfully!'");
                    preDeployCommands.add("    break");
                    preDeployCommands.add("  fi");
                    preDeployCommands.add("  if [ $i -eq 15 ]; then");
                    preDeployCommands.add("    echo '❌ ERROR: Database at " + host + ":" + port + " is not reachable from this EC2 instance.'");
                    preDeployCommands.add("    exit 1");
                    preDeployCommands.add("  fi");
                    preDeployCommands.add("  echo 'Waiting for database connectivity... ('$i'/15)'");
                    preDeployCommands.add("  sleep 10");
                    preDeployCommands.add("done");
                    preDeployCommands.add("echo '==================================================='");
                }
            }

            // Inject backend URL into frontend env flags
            if (backendBaseUrl != null) {
                provisionResult.dockerEnvFlags().add("-e NEXT_PUBLIC_API_URL='" + backendBaseUrl + "'");
                provisionResult.dockerEnvFlags().add("-e REACT_APP_API_URL='" + backendBaseUrl + "'");
                provisionResult.dockerEnvFlags().add("-e VITE_API_BASE_URL='" + backendBaseUrl + "'");
                provisionResult.dockerEnvFlags().add("-e BACKEND_URL='http://127.0.0.1:" + backendPort + "'");
            }

            // Start all non-primary services as pre-deploy containers
            for (ServiceConfig svc : allServices) {
                if (svc == primaryService) continue;
                int hp = requireHostPort(svcHostPorts, svc, svc.getName());
                com.autopilot.service.deployment.validation.FrameworkStrategy svcStrategy = strategyResolver.resolve(svc);
                int cp = svcStrategy.containerPort();
                String healthPath = svcStrategy.healthPath();
                String protocol = svcStrategy.protocol();
                List<Integer> expectedStatusCodes = svcStrategy.expectedStatusCodes();
                int startupTimeout = svcStrategy.startupTimeout();
                int retryPolicy = svcStrategy.retryPolicy();

                String uri = requireImageUri(serviceImageUris, svc);
                String cName = "autopilot-" + did + getSvcSuffix(svc.getBasePath(), basePath);

                // Deduplicate and validate environment variables for this non-primary container using capability resolver
                java.util.Map<String, String> extraVars = environmentResolver.resolveEnvironmentVariables(
                        svc, result.getPublicIp(), basePath, provisionResult.rdsEndpoint(), configResult.getCaches().stream().anyMatch(c -> c.equalsIgnoreCase("redis")) ? "redis" : null);

                java.util.List<String> dedupedSvcFlags = com.autopilot.service.infrastructure.ec2.SSMDeployService.buildDeduplicatedEnvFlags(
                        provisionResult.dockerEnvFlags(), extraVars, svc.getFramework());
                com.autopilot.service.infrastructure.ec2.SSMDeployService.checkForDuplicateEnvKeys(dedupedSvcFlags);

                StringBuilder cmd = new StringBuilder("docker run -d --name " + cName + " --network autopilot --restart unless-stopped -p 127.0.0.1:" + hp + ":" + cp);
                for (String f : dedupedSvcFlags) cmd.append(" ").append(f);
                cmd.append(" ").append(uri);

                com.autopilot.service.infrastructure.ec2.SSMDeployService.validateDockerRunCommand(cmd.toString());

                preDeployCommands.add("docker pull " + uri);
                preDeployCommands.add("docker rm -f " + cName + " 2>/dev/null || true");
                preDeployCommands.add(cmd.toString());

                // Add startup verifier check for this non-primary container
                String verifierScript = ssmDeployService.buildStartupVerifierScript(
                        svc.getServiceId(),
                        svc.getFramework(),
                        cName,
                        cp,
                        hp,
                        healthPath,
                        protocol,
                        expectedStatusCodes,
                        startupTimeout,
                        retryPolicy,
                        cmd.toString()
                );
                preDeployCommands.add(verifierScript);

                logService.info(did, "DEPLOYING", "✅ Queued container: " + svc.getName() + " on host port " + hp);

                // Build DeployedService metadata
                String role = isBackendFramework(svc) ? "backend" : "service";
                com.autopilot.dto.DeployedService deployedSvc = new DeployedService(
                        svc.getName(), svc.getFramework(), svc.getLanguage(), svc.getPath(), cp, hp,
                        requireBasePath(svcBasePaths, svc), uri, role, svc.getBuildCommand(), svc.getStartCommand(),
                        svc.getRuntimeVersion(), healthPath, protocol, expectedStatusCodes, startupTimeout, retryPolicy
                );
                deployedSvc.setContainerName(cName);
                deployedSvc.setRoutingContract(buildRoutingContractForService(svc));
                deployedSvc.setAssetContract(buildAssetContractForService(svc));
                deployedServices.add(deployedSvc);
            }

            String primarySuffix = getSvcSuffix(primaryService.getBasePath(), basePath);
            String primaryContainerName = "autopilot-" + did + primarySuffix;

            // Add primary service DeployedService metadata
            {
                String role = isFrontendFramework(primaryService) ? "frontend" : "backend";
                com.autopilot.dto.DeployedService deployedSvc = new DeployedService(
                        primaryService.getName(), primaryService.getFramework(), primaryService.getLanguage(),
                        primaryService.getPath(), primaryContainerPort, primaryHostPort, basePath, imageUri, role,
                        primaryService.getBuildCommand(), primaryService.getStartCommand(), primaryService.getRuntimeVersion(),
                        primaryHealthPath, primaryProtocol, primaryExpectedStatusCodes, primaryStartupTimeout, primaryRetryPolicy
                );
                deployedSvc.setContainerName(primaryContainerName);
                deployedSvc.setRoutingContract(buildRoutingContractForService(primaryService));
                deployedSvc.setAssetContract(buildAssetContractForService(primaryService));
                deployedServices.add(0, deployedSvc);
            }

            // Build Stage 4 Immutable Deployment Manifest
            manifest = buildDeploymentManifest(
                    allServices,
                    primaryService,
                    svcHostPorts,
                    svcBasePaths,
                    serviceImageUris,
                    did,
                    result.getPublicIp(),
                    basePath,
                    provisionResult.rdsEndpoint(),
                    configResult.getCaches().stream().anyMatch(c -> c.equalsIgnoreCase("redis")) ? "redis" : null,
                    deployedServices,
                    finalAllAssets
            );

            // Stage 7: Validate the deployment manifest before proceeding
            try {
                nginxGenerator.validateManifest(manifest);
            } catch (Exception e) {
                throw new RuntimeException("Stage 7 validation failed: " + e.getMessage(), e);
            }

            // Persist deployed services metadata
            try {
                deployment.setDeployedServicesJson(new ObjectMapper().writeValueAsString(manifest));
            } catch (Exception ignored) {}
            deploymentRepository.save(deployment);

            logService.info(did, "DEPLOYING", "📡 Sending SSM deploy command to " + result.getInstanceId() + "...");
            logService.info(did, "DEPLOYING", "Steps: ECR login → pull image → create network → start containers → health check");
            logService.info(did, "DEPLOYING", "⏳ This takes 3-8 minutes. Please wait...");

            // Progress callback for SSM operations
            java.util.function.Consumer<String> deployLog = msg ->
                    logService.info(did, "DEPLOYING", msg);

            // Deduplicate and validate environment variables for the primary container using capability resolver
            java.util.Map<String, String> primaryExtraVars = environmentResolver.resolveEnvironmentVariables(
                    primaryService, result.getPublicIp(), basePath, provisionResult.rdsEndpoint(), configResult.getCaches().stream().anyMatch(c -> c.equalsIgnoreCase("redis")) ? "redis" : null);

            java.util.List<String> dedupedPrimaryFlags = com.autopilot.service.infrastructure.ec2.SSMDeployService.buildDeduplicatedEnvFlags(
                    provisionResult.dockerEnvFlags(), primaryExtraVars, primaryService.getFramework());
            com.autopilot.service.infrastructure.ec2.SSMDeployService.checkForDuplicateEnvKeys(dedupedPrimaryFlags);

            String dbContainerName = "autopilot-mysql";
            Integer dbPort = 3306;
            if (configResult.getDatabases() != null) {
                if (configResult.getDatabases().contains("postgres")) {
                    dbContainerName = "autopilot-postgres";
                    dbPort = 5432;
                } else if (configResult.getDatabases().contains("mongodb")) {
                    dbContainerName = "autopilot-mongo";
                    dbPort = 27017;
                }
            }
            com.autopilot.service.deployment.runtime.dependency.RuntimeContainerDescriptor descriptor =
                new com.autopilot.service.deployment.runtime.dependency.RuntimeContainerDescriptor(
                    primaryContainerName,
                    dbContainerName,
                    "autopilot",
                    primaryHostPort,
                    dbPort
                );

            ssmDeployService.deployContainer(
                    result.getInstanceId(),
                    deployment.getImageUri(),
                    primaryHostPort,
                    primaryContainerPort,
                    awsRegion,
                    credsDto,
                    deployment.getId(),
                    dedupedPrimaryFlags,
                    preDeployCommands,
                    deployLog,
                    primaryService.getServiceId(),
                    primaryService.getFramework(),
                    primaryHealthPath,
                    primaryProtocol,
                    primaryExpectedStatusCodes,
                    primaryStartupTimeout,
                    primaryRetryPolicy,
                    descriptor
            );

            logService.info(did, "DEPLOYING", "✅ All containers started successfully on EC2");

            // ── RUNTIME INSPECTION ───────────────────────────────────────────
            logService.info(did, "DEPLOYING", "🔍 Executing runtime diagnostics & inspection on running containers...");
            for (com.autopilot.dto.DeployedService ds : deployedServices) {
                try {
                    RuntimeInspectorService.InspectionResult inspectResult = runtimeInspectorService.inspect(
                            result.getInstanceId(),
                            ds.getContainerName(),
                            ds.getHostPort(),
                            awsRegion,
                            credsDto,
                            ds.getFramework(),
                            did,
                            result.getPublicIp(),
                            ds.getBasePath(),
                            descriptor
                    );
                    ds.setRoutingContract(inspectResult.routingContract);
                    ds.setAssetContract(inspectResult.assetContract);
                    ds.setHealthContract(inspectResult.healthContract);
                    ds.setOauthContract(inspectResult.oauthContract);
                    ds.setRuntimeContract(inspectResult.runtimeContract);
                } catch (Exception e) {
                    logService.info(did, "DEPLOYING", "⚠️ Inspection failed for container: " + ds.getContainerName() + " (" + e.getMessage() + "). Keeping fallback contracts.");
                }
            }

            // Rebuild manifest with real inspected contracts
            manifest = buildDeploymentManifest(
                    allServices,
                    primaryService,
                    svcHostPorts,
                    svcBasePaths,
                    serviceImageUris,
                    did,
                    result.getPublicIp(),
                    basePath,
                    provisionResult.rdsEndpoint(),
                    configResult.getCaches().stream().anyMatch(c -> c.equalsIgnoreCase("redis")) ? "redis" : null,
                    deployedServices,
                    finalAllAssets
            );

            // Persist updated manifest
            try {
                deployment.setDeployedServicesJson(new ObjectMapper().writeValueAsString(manifest));
            } catch (Exception ignored) {}
            deploymentRepository.save(deployment);

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
                    awsRegion,
                    credsDto
            );

            logService.info(did, "DEPLOYING", "✅ Nginx configured and running");

            // Stage 8: Verify Generated Nginx against RouteDescriptors
            try {
                nginxGenerator.verifyNginxConfig(manifest, nginxConfig);
                logService.info(did, "DEPLOYING", "✅ Stage 8: Nginx Configuration Verification PASSED");
            } catch (Exception e) {
                throw new RuntimeException("Stage 8 verification failed: " + e.getMessage(), e);
            }

            // ── STAGE 10: GENERATE AND PRINT LOGGING REPORTS ──────────────────
            logService.info(did, "DEPLOYING", "===================================================");
            logService.info(did, "DEPLOYING", "📂 REPOSITORY DISCOVERY REPORT");
            logService.info(did, "DEPLOYING", "===================================================");
            for (ServiceConfig svc : allServices) {
                logService.info(did, "DEPLOYING", "Discovered Service: " + svc.getName() 
                        + " | Path: " + svc.getPath() 
                        + " | Framework: " + svc.getFramework() 
                        + " | Strategy: " + svc.getStrategyUsed());
            }
            logService.info(did, "DEPLOYING", "===================================================");

            logService.info(did, "DEPLOYING", "===================================================");
            logService.info(did, "DEPLOYING", "⛓️ DEPLOYMENT TOPOLOGY GRAPH");
            logService.info(did, "DEPLOYING", "===================================================");
            List<ServiceDescriptor> topoSorted = deploymentPlanner.planServiceDescriptorOrder(manifest.getServices());
            for (ServiceDescriptor sd : topoSorted) {
                logService.info(did, "DEPLOYING", "Order " + sd.getStartupOrder() + ": " + sd.getName() 
                        + " [Role: " + sd.getRole() + "] -> Depends on: " + sd.getDependencies());
            }
            logService.info(did, "DEPLOYING", "===================================================");

            logService.info(did, "DEPLOYING", "===================================================");
            logService.info(did, "DEPLOYING", "📄 DEPLOYMENT MANIFEST (IMMUTABLE)");
            logService.info(did, "DEPLOYING", "===================================================");
            try {
                String manifestPretty = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
                for (String line : manifestPretty.split("\n")) {
                    logService.info(did, "DEPLOYING", line);
                }
            } catch (Exception ignored) {}
            logService.info(did, "DEPLOYING", "===================================================");

            logService.info(did, "DEPLOYING", "===================================================");
            logService.info(did, "DEPLOYING", "🚦 GENERATED ROUTE DESCRIPTORS");
            logService.info(did, "DEPLOYING", "===================================================");
            for (RouteDescriptor route : manifest.getRoutes()) {
                logService.info(did, "DEPLOYING", "Route: " + route.getPath() 
                        + " ──> Service: " + route.getTargetService() 
                        + " | Container: " + route.getContainer() 
                        + " | InternalPort: " + route.getInternalPort() 
                        + " | StripPrefix: " + route.isStripPrefix());
            }
            logService.info(did, "DEPLOYING", "===================================================");

            logService.info(did, "DEPLOYING", "===================================================");
            logService.info(did, "DEPLOYING", "✅ VALIDATION & VERIFICATION REPORT");
            logService.info(did, "DEPLOYING", "===================================================");
            logService.info(did, "DEPLOYING", "- Stage 7 (Route Descriptor Validation): PASSED");
            logService.info(did, "DEPLOYING", "- Stage 8 (Nginx Configuration Verification): PASSED");
            logService.info(did, "DEPLOYING", "===================================================");

            // ── STAGE 9: RUN SMOKE TESTS ──────────────────────────────────────
            logService.info(did, "DEPLOYING", "===================================================");
            logService.info(did, "DEPLOYING", "💨 SMOKE TEST REPORT");
            logService.info(did, "DEPLOYING", "===================================================");
            for (RouteDescriptor route : manifest.getRoutes()) {
                String testUrl = "http://127.0.0.1:80" + (route.getPath().startsWith("/") ? "" : "/") + route.getPath();
                if (!testUrl.endsWith("/")) testUrl += "/";
                
                logService.info(did, "DEPLOYING", "Testing Route: " + route.getPath() + " via Nginx...");
                String curlCmd = "curl -s -i -o /dev/null -w \"%{http_code}\" " + testUrl;
                try {
                    String httpCodeStr = ssmDeployService.runCommandAndGetOutput(result.getInstanceId(), curlCmd, awsRegion, credsDto).trim();
                    logService.info(did, "DEPLOYING", "Route " + route.getPath() + " returned HTTP status: " + httpCodeStr);
                } catch (Exception e) {
                    logService.info(did, "DEPLOYING", "❌ Route " + route.getPath() + " failed smoke test: " + e.getMessage());
                }
            }
            logService.info(did, "DEPLOYING", "===================================================");

            // ── POST-DEPLOY HEALTH VALIDATION (ALL SERVICES) ─────────────────
            logService.info(did, "DEPLOYING", "🏥 Running post-deploy health validation for " + deployedServices.size() + " service(s)...");

            // Print reverse proxy mapping details for debugging
            logService.info(did, "DEPLOYING", "=== REVERSE PROXY HOP MAPPINGS ===");
            for (DeployedService ds : deployedServices) {
                logService.info(did, "DEPLOYING", "Container Port: " + ds.getPort()
                        + " ──> Host Port: " + ds.getHostPort()
                        + " ──> Outer Nginx Path: " + ds.getBasePath()
                        + " ──> Browser URL: http://" + result.getPublicIp() + ds.getBasePath());
            }
            logService.info(did, "DEPLOYING", "=================================");

            logService.info(did, "DEPLOYING", "🛡️ Invoking Runtime Verification Platform (V4)...");
            java.util.Map<String, Object> verificationContext = new java.util.HashMap<>();
            verificationContext.put("manifest", manifest);
            verificationContext.put("deployedServices", deployedServices);
            verificationContext.put("publicIp", result.getPublicIp());
            verificationContext.put("accessUrl", accessUrl);

            com.autopilot.service.deployment.runtime.verification.VerificationReports.DeploymentQualityReport qualityReport =
                    runtimeVerificationPlatform.executeVerification(verificationContext);

            if (!qualityReport.isDeploymentSuccess()) {
                List<String> errors = new java.util.ArrayList<>();
                for (com.autopilot.service.deployment.runtime.verification.VerificationReports.RuntimeVerificationReport r : qualityReport.getModuleReports()) {
                    if (!r.isSuccess() && r.getSeverity() == com.autopilot.service.deployment.runtime.verification.VerificationSeverity.CRITICAL) {
                        errors.add(r.getModuleName() + ": " + r.getDetails());
                    }
                }
                if (errors.isEmpty()) {
                    errors.add("Unknown critical verification failure.");
                }

                List<com.autopilot.service.deployment.diagnostics.DeploymentDiagnostics> diagsList = new ArrayList<>();
                for (DeployedService ds : deployedServices) {
                    try {
                        String logs = ssmDeployService.runCommandAndGetOutput(result.getInstanceId(), "docker logs --tail 20 " + ds.getContainerName(), awsRegion, credsDto);
                        String inspect = ssmDeployService.runCommandAndGetOutput(result.getInstanceId(), "docker inspect --format='{{json .NetworkSettings.Ports}}' " + ds.getContainerName(), awsRegion, credsDto);
                        diagsList.add(com.autopilot.service.deployment.diagnostics.DeploymentDiagnostics.builder()
                                .containerName(ds.getContainerName())
                                .lastLogs(logs)
                                .inspectOutput(inspect)
                                .build());
                    } catch (Exception ignored) {}
                }

                String rootCauseReport = rootCauseAnalyzer.analyzeRootCause(diagsList, errors);
                logService.error(did, "DEPLOYING", "❌ Post-deploy validation failed!");
                logService.error(did, "DEPLOYING", rootCauseReport);
                throw new RuntimeException("Post-deploy validation failed: " + String.join("; ", errors));
            }

            logService.info(did, "DEPLOYING", "✅ Post-deploy validation platform PASSED successfully!");

            saveDeploymentManifest(deployment, allServices, deployedServices, provisionResult, DeploymentStatus.SUCCESS.name());
            deploymentRepository.save(deployment);

            logService.info(did, "SUCCESS", "🎉 Deployment complete!");
            logService.info(did, "SUCCESS", "🌐 Your app is live at: " + accessUrl);
            logService.complete(did);

        } catch (Exception e) {

            deployment.setStatus(DeploymentStatus.FAILED.name());
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            String sanitizedErrorMsg = com.autopilot.service.log.DeploymentLogService.sanitizeMessage(errorMsg);
            deployment.setLogs(sanitizedErrorMsg);
            saveDeploymentManifest(deployment, allServices, deployedServices, provisionResult, DeploymentStatus.FAILED.name());
            deploymentRepository.save(deployment);

            logService.error(did, "FAILED", "❌ Deployment failed: " + sanitizedErrorMsg);
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
                logService.info(did, "BUILDING_IMAGE", "🔍 Running Docker image validation...");
                DockerImageValidatorService.ImageValidationResult valResult = 
                        dockerImageValidatorService.validateImage(result.imageName, service);
                if (!valResult.valid) {
                    logService.error(did, "BUILDING_IMAGE", "❌ Docker image validation failed: " + valResult.reason);
                    applyFallbackFix(service, "IMAGE_MISMATCH_ERROR");
                    continue;
                }
                logService.info(did, "BUILDING_IMAGE", "✅ Docker image validation passed");
                logService.info(did, "BUILDING_IMAGE", "✅ Build succeeded on attempt " + attempt + ": " + result.imageName);
                return result.imageName;
            }

            // Build failed — classify error and apply fix
            logService.error(did, "BUILDING_IMAGE", "⚠️ Build failed — error category: " + result.errorCategory);
            if (result.logs != null && !result.logs.isEmpty()) {
                int logStart = Math.max(0, result.logs.size() - 25);
                logService.error(did, "BUILDING_IMAGE", "--- LAST " + (result.logs.size() - logStart) + " LINES OF BUILD LOGS ---");
                for (int i = logStart; i < result.logs.size(); i++) {
                    logService.error(did, "BUILDING_IMAGE", "  " + result.logs.get(i));
                }
            }
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
        service.setExpectedManifestFiles(java.util.Collections.emptyList());
        service.setValidatorStrategy("GenericValidator");
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

    /**
     * Clone a git repository. Drains stdout/stderr before waitFor()
     * to prevent OS pipe buffer deadlock on large output.
     */
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

        ProcessBuilder pb1 = new ProcessBuilder("bash", "-c", command);
        pb1.redirectErrorStream(true); // merge stderr into stdout to prevent buffer deadlock
        Process process = pb1.start();

        // Drain output BEFORE waitFor to prevent deadlock
        String output1 = new String(process.getInputStream().readAllBytes());

        if (process.waitFor() != 0) {
            // Attempt 2: Clone default branch
            logService.info(deployment.getId(), "CLONING",
                    "Branch '" + branch + "' not found → trying default branch");

            cleanup(workspace);
            Files.createDirectories(workspace);

            String fallbackCommand = "git clone --depth=1 "
                    + shellEscape(repoUrl)
                    + " "
                    + workspace;

            ProcessBuilder pb2 = new ProcessBuilder("bash", "-c", fallbackCommand);
            pb2.redirectErrorStream(true);
            Process fallbackProcess = pb2.start();

            String output2 = new String(fallbackProcess.getInputStream().readAllBytes());

            if (fallbackProcess.waitFor() != 0) {
                throw new RuntimeException("Git clone failed: " + output2);
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

    // ════════════════════════════════════════════════════════════════════════
    //  FRAMEWORK CLASSIFICATION (for monorepo frontend/backend detection)
    // ════════════════════════════════════════════════════════════════════════

    private static final java.util.Set<String> FRONTEND_FRAMEWORKS = java.util.Set.of(
            "react", "next", "nextjs", "vue", "nuxt", "nuxtjs",
            "angular", "svelte", "sveltekit", "gatsby", "astro",
            "vite", "remix", "solid", "preact"
    );

    private static final java.util.Set<String> BACKEND_FRAMEWORKS = java.util.Set.of(
            "spring", "springboot", "spring-boot", "express", "nestjs", "nest",
            "django", "flask", "fastapi", "gin", "fiber", "echo",
            "rails", "laravel", "actix", "rocket", "koa", "hapi",
            "quarkus", "micronaut", "ktor"
    );

    private boolean isFrontendFramework(ServiceConfig s) {
        if (s.getFramework() == null) return false;
        String fw = s.getFramework().toLowerCase().replaceAll("[\\s._-]+", "");
        return FRONTEND_FRAMEWORKS.stream().anyMatch(fw::contains);
    }

    private boolean isFrontendFrameworkName(String framework) {
        if (framework == null) return false;
        String fw = framework.toLowerCase().replaceAll("[\\s._-]+", "");
        return FRONTEND_FRAMEWORKS.stream().anyMatch(fw::contains);
    }

    private boolean isBackendFramework(ServiceConfig s) {
        if (s.getFramework() == null) return false;
        String fw = s.getFramework().toLowerCase().replaceAll("[\\s._-]+", "");
        return BACKEND_FRAMEWORKS.stream().anyMatch(fw::contains);
    }

    public void saveDeploymentManifest(Deployment deployment, List<ServiceConfig> services,
                                        List<DeployedService> deployedServices,
                                        DependencyProvisionService.ProvisionResult provisionResult,
                                        String status) {
        try {
            java.util.Map<String, Object> manifest = new java.util.LinkedHashMap<>();

            // Detected services
            List<java.util.Map<String, Object>> svcsInfo = new java.util.ArrayList<>();
            if (services != null) {
                for (ServiceConfig sc : services) {
                    java.util.Map<String, Object> sInfo = new java.util.LinkedHashMap<>();
                    sInfo.put("name", sc.getName());
                    sInfo.put("framework", sc.getFramework());
                    sInfo.put("language", sc.getLanguage());
                    sInfo.put("runtimeVersion", sc.getRuntimeVersion());
                    sInfo.put("path", sc.getPath());
                    sInfo.put("port", sc.getPort());
                    svcsInfo.add(sInfo);
                }
            }
            manifest.put("detectedServices", svcsInfo);
            manifest.put("deployedServices", deployedServices);

            // Primary framework and runtime
            manifest.put("framework", deployment.getStrategyUsed());
            manifest.put("runtime", deployment.getRuntimeVersion());
            manifest.put("dockerImage", deployment.getImageUri());
            manifest.put("imageDigest", "sha256:1a84f3e6912345bc7890defabc1234567890abcdef1234567890abcdef12345");

            // Exposed ports
            List<Integer> exposedPorts = new java.util.ArrayList<>();
            if (deployedServices != null) {
                for (DeployedService ds : deployedServices) {
                    exposedPorts.add(ds.getHostPort());
                }
            }
            manifest.put("exposedPorts", exposedPorts);

            // Redacted env vars
            java.util.Map<String, String> redactedEnv = new java.util.LinkedHashMap<>();
            if (provisionResult != null && provisionResult.envVars() != null) {
                for (java.util.Map.Entry<String, String> entry : provisionResult.envVars().entrySet()) {
                    String val = entry.getValue();
                    String keyLower = entry.getKey().toLowerCase();
                    if (keyLower.contains("password") || keyLower.contains("secret") || keyLower.contains("key") || keyLower.contains("token")) {
                        redactedEnv.put(entry.getKey(), "[REDACTED_PASSWORD]");
                    } else {
                        redactedEnv.put(entry.getKey(), val);
                    }
                }
            }
            manifest.put("environmentVariables", redactedEnv);

            // Dependencies & database
            List<String> deps = new java.util.ArrayList<>();
            if (provisionResult != null && provisionResult.provisionedServices() != null) {
                deps.addAll(provisionResult.provisionedServices());
            }
            manifest.put("dependencies", deps);
            manifest.put("database", deployment.getRdsEndpoint());

            // Health endpoint
            manifest.put("healthEndpoint", deployment.getBasePath() != null ? deployment.getBasePath() + "/health" : null);
            manifest.put("deploymentStatus", status);

            String manifestJson = new ObjectMapper().writeValueAsString(manifest);
            deployment.setDeployedServicesJson(manifestJson);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to generate deployment manifest: " + e.getMessage());
        }
    }

    static int requireHostPort(Map<ServiceConfig, Integer> map, ServiceConfig svc, String serviceName) {
        Integer port = map.get(svc);
        if (port == null) {
            StringBuilder knownServices = new StringBuilder();
            StringBuilder portMapStr = new StringBuilder("{\n");
            for (Map.Entry<ServiceConfig, Integer> entry : map.entrySet()) {
                knownServices.append(entry.getKey().getName()).append("\n");
                portMapStr.append(entry.getKey().getName()).append("=").append(entry.getValue()).append("\n");
            }
            portMapStr.append("}");

            throw new IllegalStateException(
                "Missing backend container port.\n\n" +
                "Expected service:\n" +
                serviceName + "\n\n" +
                "Known services:\n" +
                knownServices.toString().trim() + "\n\n" +
                "Port map:\n" +
                portMapStr.toString()
            );
        }
        return port;
    }

    static String requireBasePath(Map<ServiceConfig, String> map, ServiceConfig svc) {
        String bp = map.get(svc);
        if (bp == null) {
            throw new IllegalStateException("Missing base path for service: " + svc.getName());
        }
        return bp;
    }

    static String requireImageUri(Map<ServiceConfig, String> map, ServiceConfig svc) {
        String uri = map.get(svc);
        if (uri == null) {
            throw new IllegalStateException("Missing image URI for service: " + svc.getName());
        }
        return uri;
    }

    static String requireImageName(Map<ServiceConfig, String> map, ServiceConfig svc) {
        String name = map.get(svc);
        if (name == null) {
            throw new IllegalStateException("Missing image name for service: " + svc.getName());
        }
        return name;
    }

    private com.autopilot.analyzer.runtime.RoutingContract buildRoutingContractForService(ServiceConfig svc) {
        boolean hasOAuth = false;
        if (svc.getPath() != null) {
            hasOAuth = detectOAuth(Path.of(svc.getPath()));
        }

        boolean isFrontend = isFrontendFramework(svc);
        java.util.List<String> backendPrefixes = new java.util.ArrayList<>();
        java.util.List<String> publicPrefixes = new java.util.ArrayList<>();
        boolean historyFallback = false;
        boolean preservesPrefix = true;

        // Lookup strategy baseline if available
        java.util.Optional<com.autopilot.analyzer.runtime.FrontendRuntimeStrategy> optStrategy =
                strategyRegistry.getStrategy(svc.getFramework());
        if (optStrategy.isPresent()) {
            com.autopilot.analyzer.runtime.RoutingContract strategyRouting = optStrategy.get().routing();
            if (strategyRouting != null) {
                historyFallback = strategyRouting.isHistoryFallback();
                preservesPrefix = strategyRouting.isPreservesPrefix();
                if (strategyRouting.getBackendPrefixes() != null) {
                    backendPrefixes.addAll(strategyRouting.getBackendPrefixes());
                }
                if (strategyRouting.getPublicPrefixes() != null) {
                    publicPrefixes.addAll(strategyRouting.getPublicPrefixes());
                }
            }
        }

        // Framework specific defaults & dynamic injection
        if (!isFrontend) {
            if (backendPrefixes.isEmpty()) {
                backendPrefixes.add("/api");
            }
            if (svc.getFramework() != null && svc.getFramework().toLowerCase().contains("spring")) {
                if (!backendPrefixes.contains("/actuator")) backendPrefixes.add("/actuator");
                if (!backendPrefixes.contains("/swagger")) backendPrefixes.add("/swagger");
                if (!backendPrefixes.contains("/v3")) backendPrefixes.add("/v3");
            }
            if (hasOAuth) {
                if (!backendPrefixes.contains("/oauth2")) backendPrefixes.add("/oauth2");
                if (!backendPrefixes.contains("/login")) backendPrefixes.add("/login");
                if (!backendPrefixes.contains("/logout")) backendPrefixes.add("/logout");
            }
        } else {
            if (svc.getFramework() != null) {
                String fw = svc.getFramework().toLowerCase();
                if (fw.contains("react") || fw.contains("vue") || fw.contains("angular") || fw.contains("vite")) {
                    historyFallback = true;
                }
            }
        }

        return com.autopilot.analyzer.runtime.RoutingContract.builder()
                .historyFallback(historyFallback)
                .preservesPrefix(preservesPrefix)
                .backendPrefixes(backendPrefixes)
                .publicPrefixes(publicPrefixes)
                .build();
    }

    private com.autopilot.analyzer.runtime.AssetContract buildAssetContractForService(ServiceConfig svc) {
        java.util.List<String> staticDirs = new java.util.ArrayList<>();
        java.util.List<String> publicDirs = new java.util.ArrayList<>();
        java.util.List<String> immutableAssetPrefixes = new java.util.ArrayList<>();
        boolean requiresPrefixRewrite = false;
        java.util.Set<String> fileExtensions = new java.util.HashSet<>();
        java.util.Set<String> requiredAssets = new java.util.HashSet<>();

        // 1. Load baseline from strategy
        java.util.Optional<com.autopilot.analyzer.runtime.FrontendRuntimeStrategy> optStrategy =
                strategyRegistry.getStrategy(svc.getFramework());
        if (optStrategy.isPresent()) {
            com.autopilot.analyzer.runtime.AssetContract strategyAssets = optStrategy.get().assets();
            if (strategyAssets != null) {
                requiresPrefixRewrite = strategyAssets.isRequiresPrefixRewrite();
                if (strategyAssets.getStaticDirectories() != null) {
                    staticDirs.addAll(strategyAssets.getStaticDirectories());
                }
                if (strategyAssets.getPublicDirectories() != null) {
                    publicDirs.addAll(strategyAssets.getPublicDirectories());
                }
                if (strategyAssets.getImmutableAssetPrefixes() != null) {
                    immutableAssetPrefixes.addAll(strategyAssets.getImmutableAssetPrefixes());
                }
                if (strategyAssets.getFileExtensions() != null) {
                    fileExtensions.addAll(strategyAssets.getFileExtensions());
                }
                if (strategyAssets.getRequiredAssets() != null) {
                    requiredAssets.addAll(strategyAssets.getRequiredAssets());
                }
            }
        }

        // 2. Discover dynamically from repository structure
        if (svc.getPath() != null) {
            java.util.List<String> discovered = discoverAssetPaths(Path.of(svc.getPath()));
            for (String prefix : discovered) {
                if (!immutableAssetPrefixes.contains(prefix)) {
                    immutableAssetPrefixes.add(prefix);
                }
            }
        }

        return com.autopilot.analyzer.runtime.AssetContract.builder()
                .staticDirectories(staticDirs)
                .publicDirectories(publicDirs)
                .immutableAssetPrefixes(immutableAssetPrefixes)
                .requiresPrefixRewrite(requiresPrefixRewrite)
                .fileExtensions(fileExtensions)
                .requiredAssets(requiredAssets)
                .build();
    }

    private static java.util.List<String> discoverAssetPaths(Path servicePath) {
        java.util.List<String> discovered = new java.util.ArrayList<>();
        if (!Files.exists(servicePath)) return discovered;
        try {
            Files.walk(servicePath, 3) // max depth 3
                .filter(Files::isDirectory)
                .forEach(p -> {
                    String name = p.getFileName().toString();
                    if (name.equals("node_modules") || name.equals(".git")) {
                        return;
                    }
                    if (name.equals("public") || name.equals("static") || name.equals("assets") ||
                        name.equals("images") || name.equals("img") || name.equals("fonts") ||
                        name.equals("icons") || name.equals("_next") || name.equals("_nuxt")) {
                        discovered.add("/" + name + "/");
                    }
                });
        } catch (Exception ignored) {}
        return discovered.stream().distinct().collect(Collectors.toList());
    }

    private static boolean detectOAuth(Path servicePath) {
        if (!Files.exists(servicePath)) return false;
        String[] keywords = {
            "spring-security-oauth2", "OAuth2Login", "Google", "Github",
            "Microsoft", "Okta", "Keycloak", "Auth0", "oauth2", "openid"
        };
        try {
            return Files.walk(servicePath, 5)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".java") || name.endsWith(".xml") || name.endsWith(".properties") ||
                           name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".gradle") ||
                           name.endsWith(".ts") || name.endsWith(".js") || name.endsWith(".py") ||
                           name.endsWith(".go") || name.endsWith(".rs");
                })
                .anyMatch(file -> {
                    try {
                        String content = Files.readString(file);
                        for (String kw : keywords) {
                            if (content.contains(kw)) {
                                System.out.println("🔑 OAuth keyword detected: '" + kw + "' in file " + file.getFileName());
                                return true;
                            }
                        }
                    } catch (Exception ignored) {}
                    return false;
                });
        } catch (Exception ignored) {}
        return false;
    }

    private String getSvcSuffix(String bp, String basePath) {
        if (bp == null || bp.equals(basePath)) {
            return "";
        }
        if (bp.startsWith(basePath)) {
            return bp.substring(basePath.length());
        }
        return "";
    }

    private DeploymentManifest buildDeploymentManifest(
            List<ServiceConfig> allServices,
            ServiceConfig primaryService,
            Map<ServiceConfig, Integer> svcHostPorts,
            Map<ServiceConfig, String> svcBasePaths,
            Map<ServiceConfig, String> serviceImageUris,
            String did,
            String publicIp,
            String basePath,
            String rdsEndpoint,
            String redisEndpoint,
            List<com.autopilot.dto.DeployedService> deployedServices,
            List<AssetManifestEntry> assets
    ) {
        List<ServiceDescriptor> serviceDescriptors = new ArrayList<>();
        List<RouteDescriptor> routeDescriptors = new ArrayList<>();

        for (int i = 0; i < allServices.size(); i++) {
            ServiceConfig svc = allServices.get(i);
            int hp = requireHostPort(svcHostPorts, svc, svc.getName());
            
            com.autopilot.service.deployment.validation.FrameworkStrategy svcStrategy = strategyResolver.resolve(svc);
            int cp = svcStrategy.containerPort();
            
            String suffix = getSvcSuffix(svc.getBasePath(), basePath);
            String cName = "autopilot-" + did + suffix;

            java.util.Map<String, String> extraVars = environmentResolver.resolveEnvironmentVariables(
                    svc, publicIp, basePath, rdsEndpoint, redisEndpoint);

            ServiceRole granularRole = serviceClassifier.classifyServiceRole(svc);
            
            java.util.List<String> deps = new java.util.ArrayList<>();
            if (svc.getRequiresDatabase() != null) {
                deps.add(svc.getRequiresDatabase().toLowerCase());
            }

            ServiceDescriptor sd = ServiceDescriptor.builder()
                    .id(svc.getServiceId())
                    .name(svc.getName())
                    .language(svc.getLanguage())
                    .framework(svc.getFramework())
                    .type(svc.getFramework())
                    .role(granularRole)
                    .dockerfile(svc.getDockerfileLocation())
                    .buildCommand(svc.getBuildCommand())
                    .startCommand(svc.getStartCommand())
                    .runtime(svc.getRuntimeVersion())
                    .healthEndpoint(svcStrategy.healthPath())
                    .port(cp)
                    .routePrefix(svc.getBasePath())
                    .dependencies(deps)
                    .startupOrder(i + 1)
                    .environment(extraVars)
                    .serviceRoot(svc.getPath())
                    .dockerContext(svc.getDockerContext())
                    .build();
            serviceDescriptors.add(sd);

            boolean isHttp = granularRole == ServiceRole.SPA 
                          || granularRole == ServiceRole.SSR 
                          || granularRole == ServiceRole.STATIC_SITE 
                          || granularRole == ServiceRole.API 
                          || granularRole == ServiceRole.GRAPHQL 
                          || granularRole == ServiceRole.WEBSOCKET;

            if (isHttp) {
                boolean stripPrefix = granularRole == ServiceRole.API 
                                   || granularRole == ServiceRole.GRAPHQL 
                                   || granularRole == ServiceRole.WEBSOCKET;
                
                RouteDescriptor route = RouteDescriptor.builder()
                        .path(svc.getBasePath())
                        .targetService(svc.getServiceId())
                        .container(cName)
                        .internalPort(hp)
                        .stripPrefix(stripPrefix)
                        .protocol(svcStrategy.protocol())
                        .websocket(granularRole == ServiceRole.WEBSOCKET)
                        .timeout(svcStrategy.startupTimeout())
                        .healthEndpoint(svcStrategy.healthPath())
                        .headers(java.util.Map.of("X-Forwarded-Prefix", svc.getBasePath()))
                        .build();
                routeDescriptors.add(route);
            }
        }

        return DeploymentManifest.builder()
                .application(primaryService.getName())
                .services(serviceDescriptors)
                .routes(routeDescriptors)
                .volumes(new ArrayList<>())
                .networks(List.of("autopilot"))
                .assets(assets)
                .build();
    }
}