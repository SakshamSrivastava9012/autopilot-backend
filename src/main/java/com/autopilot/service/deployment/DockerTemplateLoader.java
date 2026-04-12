package com.autopilot.service.deployment;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class DockerTemplateLoader {

    public String loadTemplate(String framework) throws Exception {

        String path = "docker/" + framework + ".Dockerfile";

        ClassPathResource resource = new ClassPathResource(path);

        return new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }
}