package com.autopilot.analyzer.plugin;

import com.autopilot.analyzer.detectors.FrameworkPlugin;
import com.autopilot.analyzer.model.ServiceConfig;

import java.util.List;

public class SpringBootPlugin implements FrameworkPlugin {

    @Override
    public ServiceConfig detect(List<String> files) {

        for (String file : files) {

            if (file.endsWith("pom.xml") || file.endsWith("build.gradle")) {

                ServiceConfig service = new ServiceConfig();

                service.setFramework("spring-boot");
                service.setLanguage("java");
                service.setStrategyUsed("TEMPLATE");
                service.setName("spring-service");
                service.setConfidence(90);

                service.setPath(
                        file.endsWith("pom.xml")
                                ? file.replace("/pom.xml", "").replace("pom.xml", ".")
                                : file.replace("/build.gradle", "").replace("build.gradle", ".")
                );

                // Detect Java version from pom.xml path context
                // Safe default: Java 17 (widest compatibility for Spring Boot)
                service.setRuntimeVersion("17");

                boolean hasWrapper = files.stream().anyMatch(f -> f.endsWith("mvnw"));
                boolean isGradle = file.endsWith("build.gradle");

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
                        files.contains(service.getPath() + "/Dockerfile")
                                || files.contains("Dockerfile")
                );

                return service;
            }
        }

        return null;
    }
}