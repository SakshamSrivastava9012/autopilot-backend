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

                service.setName("spring-service");

                service.setPath(
                        file.endsWith("pom.xml")
                                ? file.replace("/pom.xml","")
                                : file.replace("/build.gradle","")
                );

                service.setBuildCommand("mvn clean package");

                service.setStartCommand("java -jar target/*.jar");

                service.setPort(8080);

                service.setDockerfileExists(
                        files.contains(service.getPath() + "/Dockerfile")
                );

                return service;
            }
        }

        return null;
    }
}