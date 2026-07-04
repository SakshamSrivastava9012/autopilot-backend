package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SpringBootPlugin implements FrameworkPlugin {

    @Override
    public List<ServiceConfig> detect(List<String> files) {
        List<ServiceConfig> services = new ArrayList<>();

        for (String file : files) {
            if ((file.endsWith("pom.xml") || file.endsWith("build.gradle") || file.endsWith("build.gradle.kts"))
                    && !file.contains("node_modules")) {

                ServiceConfig service = new ServiceConfig();
                service.setFramework("spring-boot");
                service.setLanguage("java");
                service.setStrategyUsed("TEMPLATE");
                
                String name = deriveServiceName(file, "spring-service");
                service.setName(name);
                service.setConfidence(90);

                Path parent = Path.of(file).getParent();
                String pathStr = parent == null ? "." : parent.toString();
                service.setPath(pathStr);

                // Detect Java version from pom.xml path context
                // Safe default: Java 17 (widest compatibility for Spring Boot)
                service.setRuntimeVersion("17");

                boolean hasWrapper = files.stream().anyMatch(f -> f.endsWith("mvnw"));
                boolean isGradle = file.endsWith("build.gradle") || file.endsWith("build.gradle.kts");

                if (isGradle) {
                    boolean hasGradleWrapper = files.stream().anyMatch(f -> f.endsWith("gradlew"));
                    service.setBuildCommand(hasGradleWrapper ? "./gradlew build -x test" : "gradle build -x test");
                    service.setStartCommand("java -jar build/libs/*.jar");
                } else {
                    service.setBuildCommand(hasWrapper ? "./mvnw clean package -DskipTests" : "mvn clean package -DskipTests");
                    service.setStartCommand("java -jar target/*.jar");
                }

                service.setPort(8080);

                service.setDockerfileExists(
                        files.contains(pathStr + "/Dockerfile")
                                || files.contains("Dockerfile")
                );

                services.add(service);
            }
        }

        return services;
    }

    private String deriveServiceName(String file, String defaultName) {
        Path parent = Path.of(file).getParent();
        if (parent == null || parent.getFileName() == null) {
            return defaultName;
        }
        return parent.getFileName().toString();
    }
}