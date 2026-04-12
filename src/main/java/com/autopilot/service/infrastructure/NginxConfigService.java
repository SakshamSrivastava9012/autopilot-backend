package com.autopilot.service.infrastructure;

import com.autopilot.entity.Deployment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NginxConfigService {

    public String generateConfig(List<Deployment> deployments) {

        StringBuilder config = new StringBuilder();

        config.append("server {\n");
        config.append("    listen 80;\n");
        config.append("    server_name _;\n\n");

        config.append("    location /health {\n");
        config.append("        return 200 'ok';\n");
        config.append("        add_header Content-Type text/plain;\n");
        config.append("    }\n\n");

        for (Deployment d : deployments) {

            if (d == null
                    || d.getAssignedPort() == null
                    || d.getBasePath() == null
                    || d.getBasePath().isBlank()) {
                continue;
            }

            String path = d.getBasePath().trim();
            int port = d.getAssignedPort();

            if (!path.startsWith("/")) path = "/" + path;
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);

            config.append("    location ").append(path).append("/ {\n");

            // ❌ NO REWRITE
            // ❌ NO PATH STRIPPING

            config.append("        proxy_pass http://127.0.0.1:").append(port).append(";\n");

            config.append("        proxy_http_version 1.1;\n");
            config.append("        proxy_set_header Host $host;\n");
            config.append("        proxy_set_header X-Real-IP $remote_addr;\n");
            config.append("        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
            config.append("        proxy_set_header X-Forwarded-Proto $scheme;\n");

            config.append("        proxy_set_header Upgrade $http_upgrade;\n");
            config.append("        proxy_set_header Connection \"upgrade\";\n");

            config.append("    }\n\n");
        }

        config.append("    location / {\n");
        config.append("        return 404;\n");
        config.append("    }\n");

        config.append("}\n");

        return config.toString();
    }
}