package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.FileWriter;

@Component
@RequiredArgsConstructor
public class DockerfileGenerator {

    private final DockerTemplateLoader templateLoader;

    public void generate(ServiceConfig service) throws Exception {

        String template =
                templateLoader.loadTemplate(service.getFramework());

        template =
                template.replace("{{PORT}}",
                        service.getPort().toString());

        String dockerfilePath =
                service.getPath() + "/Dockerfile";

        try (FileWriter writer = new FileWriter(dockerfilePath)) {

            writer.write(template);
        }
    }
}