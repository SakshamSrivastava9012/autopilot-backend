package com.autopilot.service.deployment.validation;

import com.autopilot.analyzer.model.ServiceConfig;
import java.nio.file.Path;
import java.util.List;

public class SpringBootFrameworkStrategy implements FrameworkStrategy {
    private final ServiceConfig service;

    public SpringBootFrameworkStrategy(ServiceConfig service) {
        this.service = service;
    }

    @Override
    public List<String> expectedManifestFiles() {
        return List.of("pom.xml", "build.gradle", "build.gradle.kts");
    }

    @Override
    public BuildCommand buildCommand() {
        return new BuildCommand(service.getBuildCommand());
    }

    @Override
    public DockerStrategy dockerStrategy() {
        String root = service.getServiceRoot();
        return new DockerStrategy(
            Path.of(root).resolve("Dockerfile").toAbsolutePath().normalize().toString(),
            root
        );
    }

    @Override
    public int containerPort() {
        return service.getPort() != null ? service.getPort() : 8080;
    }

    @Override
    public String healthPath() {
        if (service.getDeploymentManifest() != null && service.getDeploymentManifest().getHealthCheckPath() != null) {
            return service.getDeploymentManifest().getHealthCheckPath();
        }
        return "/actuator/health";
    }

    @Override
    public String protocol() {
        return "HTTP";
    }

    @Override
    public List<Integer> expectedStatusCodes() {
        return List.of(200, 204, 301, 302, 401, 403, 404);
    }

    @Override
    public int startupTimeout() {
        return 300;
    }

    @Override
    public int retryPolicy() {
        return 60;
    }

    @Override
    public List<String> logReadinessMarkers() {
        return List.of("Started", "Tomcat started on port", "Listening on");
    }

    @Override
    public List<String> logCrashMarkers() {
        return List.of(
            "Exception", "BeanCreationException", "FlywayException", "LiquibaseException", 
            "SQLException", "MongoTimeoutException", "ClassNotFoundException", 
            "NoSuchBeanDefinitionException", "Port already in use", "Address already in use", "FATAL"
        );
    }

    @Override
    public List<String> healthEndpoints() {
        return List.of(healthPath(), "/");
    }

    @Override
    public List<String> criticalEnvVars() {
        return List.of("SPRING_DATASOURCE_URL", "SPRING_REDIS_HOST");
    }
}

