package com.autopilot.analyzer.adapters;

import com.autopilot.analyzer.model.DeploymentManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpringBootAdapter implements FrameworkAdapter {
    @Override
    public boolean matches(Path workspace, List<String> relativeFiles) {
        return relativeFiles.stream().anyMatch(f -> f.endsWith("pom.xml") || f.endsWith("build.gradle"));
    }

    @Override
    public String detect(Path workspace, List<String> relativeFiles) {
        return "Spring Boot";
    }

    @Override
    public String buildInfo(Path workspace, List<String> relativeFiles) {
        return "Java Spring Boot application using Maven or Gradle.";
    }

    @Override
    public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, Map<String, String> envVars) {
        boolean isGradle = relativeFiles.stream().anyMatch(f -> f.endsWith("build.gradle"));
        String runtimeVersion = detectJavaVersion(workspace, relativeFiles);
        
        String buildCmd = isGradle ? "./gradlew build -x test" : "./mvnw clean package -DskipTests";
        String startCmd = isGradle ? "java -jar build/libs/*.jar" : "java -jar target/*.jar";
        String outDir = isGradle ? "build/libs" : "target";

        return DeploymentManifest.builder()
                .framework("spring-boot")
                .runtime("Java " + runtimeVersion)
                .packageManager(isGradle ? "gradle" : "maven")
                .installCommand("")
                .buildCommand(buildCmd)
                .startCommand(startCmd)
                .outputDirectory(outDir)
                .healthCheckPath("/health")
                .port(8080)
                .environmentVariables(envVars)
                .build();
    }

    private String detectJavaVersion(Path workspace, List<String> files) {
        for (String file : files) {
            if (file.endsWith("pom.xml")) {
                try {
                    String content = Files.readString(workspace.resolve(file));
                    Matcher m1 = Pattern.compile("<java\\.version>(\\d+)</java\\.version>").matcher(content);
                    if (m1.find()) return m1.group(1);
                    Matcher m2 = Pattern.compile("<maven\\.compiler\\.source>(\\d+)</maven\\.compiler\\.source>").matcher(content);
                    if (m2.find()) return m2.group(1);
                } catch (IOException ignored) {}
            } else if (file.endsWith("build.gradle")) {
                try {
                    String content = Files.readString(workspace.resolve(file));
                    Matcher m = Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?(\\d+)").matcher(content);
                    if (m.find()) return m.group(1);
                } catch (IOException ignored) {}
            }
        }
        return "21"; // default fallback
    }
}
