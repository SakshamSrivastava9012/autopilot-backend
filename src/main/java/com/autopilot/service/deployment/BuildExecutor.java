package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.entity.Deployment;
import com.autopilot.service.log.DeploymentLogService;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class BuildExecutor {

    private final DockerBuilder dockerBuilder;
    private final DockerfileGenerator dockerfileGenerator;
    private final DeploymentLogService logService;
    private final com.autopilot.service.deployment.validation.StrategyResolver strategyResolver;

    public BuildExecutor(DockerBuilder dockerBuilder, DockerfileGenerator dockerfileGenerator, DeploymentLogService logService, com.autopilot.service.deployment.validation.StrategyResolver strategyResolver) {
        this.dockerBuilder = dockerBuilder;
        this.dockerfileGenerator = dockerfileGenerator;
        this.logService = logService;
        this.strategyResolver = strategyResolver;
    }

    public DockerBuilder.BuildResult executeBuild(Deployment deployment, FrameworkMetadata metadata, String path, String suffix) throws Exception {
        String did = deployment.getId();
        
        // Convert FrameworkMetadata to temporary ServiceConfig to reuse DockerfileGenerator
        ServiceConfig service = new ServiceConfig();
        service.setName(metadata.getName());
        service.setFramework(metadata.getFrameworkType().name().toLowerCase());
        service.setPath(path);
        service.setBuildCommand(metadata.getBuildCommand());
        service.setStartCommand(metadata.getStartCommand());
        service.setPort(metadata.getPort());
        service.setLanguage(metadata.getLanguage());
        service.setRuntimeVersion(metadata.getDefaultRuntimeVersion());
        service.setDockerfileExists(metadata.isDockerfileExists());

        // Resolve Strategy and populate validation/docker metadata fields
        com.autopilot.service.deployment.validation.FrameworkStrategy strategy = strategyResolver.resolve(service);
        service.setBuildContext(service.getServiceRoot());
        service.setExpectedManifestFiles(strategy.expectedManifestFiles());
        service.setDockerStrategy(strategy.getClass().getSimpleName().replace("FrameworkStrategy", "DockerStrategy"));
        service.setValidatorStrategy(strategy.getClass().getSimpleName().replace("FrameworkStrategy", "Validator"));

        // Generate Dockerfile dynamically
        logService.info(did, "BUILDING_IMAGE", "⚙️ Generating Dockerfile using Strategy Pattern...");
        dockerfileGenerator.generateForMetadata(metadata, path);

        // Run docker build
        logService.info(did, "BUILDING_IMAGE", "🔨 Running Docker build command...");
        return dockerBuilder.buildSafeSuffix(service, did, suffix);
    }
}
