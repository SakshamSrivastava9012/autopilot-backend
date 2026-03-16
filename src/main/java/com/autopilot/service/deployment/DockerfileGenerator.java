package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class DockerfileGenerator {

    private final DockerTemplateLoader templateLoader;

    public void generate(ServiceConfig service) throws Exception {

        String template = templateLoader.loadTemplate(service.getFramework());

        template = template.replace(
                "{{PORT}}",
                service.getPort().toString()
        );

        Path servicePath = Path.of(service.getPath());

        // If path points to a file (like package.json) use its parent directory
        if (Files.isRegularFile(servicePath)) {
            servicePath = servicePath.getParent();
        }

        Path dockerfilePath = servicePath.resolve("Dockerfile");

        try (FileWriter writer = new FileWriter(dockerfilePath.toFile())) {
            writer.write(template);
        }
    }
}