package com.autopilot.service.deployment.validation;

import com.autopilot.analyzer.model.ServiceConfig;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class BuildContextValidator {

    private final StrategyResolver strategyResolver;

    public BuildContextValidator(StrategyResolver strategyResolver) {
        this.strategyResolver = strategyResolver;
    }

    public void validate(ServiceConfig service) {
        List<String> expected = service.getExpectedManifestFiles();
        if (expected == null || expected.isEmpty()) {
            FrameworkStrategy strategy = strategyResolver.resolve(service);
            expected = strategy.expectedManifestFiles();
        }

        String validatorName = service.getValidatorStrategy();
        if (validatorName == null) {
            FrameworkStrategy strategy = strategyResolver.resolve(service);
            validatorName = strategy.getClass().getSimpleName().replace("FrameworkStrategy", "Validator");
        }

        String buildContext = service.getServiceRoot();
        String dockerfile = Path.of(buildContext).resolve("Dockerfile").toAbsolutePath().normalize().toString();

        System.out.println("Validating Service:");
        System.out.println("Framework: " + service.getFramework());
        System.out.println("Validator: " + validatorName);
        System.out.println("Expected:");
        if (expected.isEmpty()) {
            System.out.println("  (None)");
        } else {
            expected.forEach(System.out::println);
        }
        System.out.println("Build Context:");
        System.out.println(buildContext);
        System.out.println("Dockerfile:");
        System.out.println(dockerfile);

        // Verify build context exists and is a directory
        Path contextPath = Path.of(buildContext);
        if (!Files.exists(contextPath) || !Files.isDirectory(contextPath)) {
            throw new IllegalArgumentException("Validation failed: build context " + buildContext + " is invalid or not a directory.");
        }

        // Validate manifest files
        if (!expected.isEmpty()) {
            boolean foundAny = false;
            for (String file : expected) {
                if (file.contains("*")) {
                    try (var stream = Files.find(contextPath, 1, (p, attr) -> p.getFileName().toString().endsWith(file.substring(1)))) {
                        if (stream.findFirst().isPresent()) {
                            foundAny = true;
                            break;
                        }
                    } catch (IOException ignored) {}
                } else {
                    if (Files.exists(contextPath.resolve(file))) {
                        foundAny = true;
                        break;
                    }
                }
            }
            if (!foundAny) {
                throw new IllegalArgumentException("Validation failed: build context " + buildContext + 
                    " is invalid. Expected manifest file " + String.join(" or ", expected) + " is missing.");
            }
        }
    }
}
