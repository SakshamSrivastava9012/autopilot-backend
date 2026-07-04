package com.autopilot.service.deployment.strategies;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.FrameworkType;
import org.springframework.stereotype.Component;
import java.util.Map;

public class ContainerStrategies {

    @Component
    public static class SpringBootContainerStrategy implements ContainerStrategy {
        @Override
        public boolean supports(FrameworkType frameworkType) {
            return frameworkType == FrameworkType.SPRING_BOOT ||
                   frameworkType == FrameworkType.QUARKUS ||
                   frameworkType == FrameworkType.MICRONAUT;
        }

        @Override
        public void populateEnvironment(FrameworkMetadata metadata, Map<String, String> envMap, String awsRegion) {
            envMap.putIfAbsent("SPRING_PROFILES_ACTIVE", "prod");
            envMap.putIfAbsent("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envMap.putIfAbsent("SERVER_PORT", String.valueOf(metadata.getPort()));

            // AWS Instance Profile Fallback
            envMap.put("SPRING_CLOUD_AWS_CREDENTIALS_INSTANCE_PROFILE", "true");
            envMap.put("SPRING_CLOUD_AWS_CREDENTIALS_USE_DEFAULT_AWS_CREDENTIALS_CHAIN", "true");
            envMap.put("SPRING_CLOUD_AWS_REGION_STATIC", awsRegion);
            envMap.put("CLOUD_AWS_CREDENTIALS_ACCESS_KEY", "placeholder-use-instance-role");
            envMap.put("CLOUD_AWS_CREDENTIALS_SECRET_KEY", "placeholder-use-instance-role");
            envMap.put("AWS_ACCESS_KEY_ID", "placeholder-use-instance-role");
            envMap.put("AWS_SECRET_ACCESS_KEY", "placeholder-use-instance-role");
        }
    }

    @Component
    public static class DjangoContainerStrategy implements ContainerStrategy {
        @Override
        public boolean supports(FrameworkType frameworkType) {
            return frameworkType == FrameworkType.DJANGO;
        }

        @Override
        public void populateEnvironment(FrameworkMetadata metadata, Map<String, String> envMap, String awsRegion) {
            envMap.putIfAbsent("DJANGO_SETTINGS_MODULE", "config.settings.production");
            envMap.putIfAbsent("DEBUG", "false");
        }
    }

    @Component
    public static class NodeContainerStrategy implements ContainerStrategy {
        @Override
        public boolean supports(FrameworkType frameworkType) {
            return frameworkType == FrameworkType.NEXTJS ||
                   frameworkType == FrameworkType.NUXT ||
                   frameworkType == FrameworkType.REMIX ||
                   frameworkType == FrameworkType.SVELTEKIT ||
                   frameworkType == FrameworkType.SOLIDSTART ||
                   frameworkType == FrameworkType.EXPRESS ||
                   frameworkType == FrameworkType.NESTJS ||
                   frameworkType == FrameworkType.FASTIFY ||
                   frameworkType == FrameworkType.KOA ||
                   frameworkType == FrameworkType.HONO ||
                   frameworkType == FrameworkType.ADONIS;
        }

        @Override
        public void populateEnvironment(FrameworkMetadata metadata, Map<String, String> envMap, String awsRegion) {
            envMap.putIfAbsent("NODE_ENV", "production");
        }
    }

    @Component
    public static class PythonContainerStrategy implements ContainerStrategy {
        @Override
        public boolean supports(FrameworkType frameworkType) {
            return frameworkType == FrameworkType.FLASK ||
                   frameworkType == FrameworkType.FASTAPI;
        }

        @Override
        public void populateEnvironment(FrameworkMetadata metadata, Map<String, String> envMap, String awsRegion) {
            envMap.putIfAbsent("FLASK_ENV", "production");
            envMap.putIfAbsent("PYTHONUNBUFFERED", "1");
        }
    }

    @Component
    public static class GoContainerStrategy implements ContainerStrategy {
        @Override
        public boolean supports(FrameworkType frameworkType) {
            return frameworkType == FrameworkType.GO;
        }

        @Override
        public void populateEnvironment(FrameworkMetadata metadata, Map<String, String> envMap, String awsRegion) {
            envMap.putIfAbsent("GIN_MODE", "release");
        }
    }

    @Component
    public static class DefaultContainerStrategy implements ContainerStrategy {
        @Override
        public boolean supports(FrameworkType frameworkType) {
            return true; // fallback
        }

        @Override
        public void populateEnvironment(FrameworkMetadata metadata, Map<String, String> envMap, String awsRegion) {
            // No custom logic needed for defaults
        }
    }
}
