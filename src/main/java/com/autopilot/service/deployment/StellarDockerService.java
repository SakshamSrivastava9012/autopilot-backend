package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StellarDockerService {

    private final StellarClient stellarClient;

    public String generateDockerfile(ServiceConfig service) {

        String prompt = """
Generate a production-ready Dockerfile.

STRICT RULES:
- Return ONLY Dockerfile
- No explanation
- No markdown
- No extra text
- Start with FROM
- End with CMD

Project:
Framework: %s
Port: %s
""".formatted(
                service.getFramework(),
                service.getPort()
        );

        System.out.println("✨ Sending request to Stellar LLM...");

        String dockerfile = stellarClient.generate(prompt);

        // 🔥 CRITICAL: fix LLM mistakes
        dockerfile = postProcessDockerfile(dockerfile, service);

        validate(dockerfile);

        System.out.println("📦 Final Dockerfile:\n" + dockerfile);

        return dockerfile;
    }

    // 🔥 POST PROCESSOR (MOST IMPORTANT PART)
    private String postProcessDockerfile(String dockerfile, ServiceConfig service) {

        if (dockerfile == null) return "";

        // normalize
        dockerfile = dockerfile.trim();

        // 🔥 FIX 1: force modern lightweight base image
        dockerfile = dockerfile.replaceAll("node:14", "node:20-alpine");
        dockerfile = dockerfile.replaceAll("node:16", "node:20-alpine");
        dockerfile = dockerfile.replaceAll("node:18", "node:20-alpine");

        String framework = service.getFramework() != null
                ? service.getFramework().toLowerCase()
                : "";

        // 🔥 FIX 2: FORCE CORRECT DOCKERFILE FOR NEXT.JS
        if (framework.contains("next")) {

            System.out.println("⚠️ Overriding Dockerfile for Next.js (LLM fix)");

            return """
            FROM node:20-alpine

            WORKDIR /app

            COPY package*.json ./
            RUN npm install

            COPY . .

            RUN npm run build

            EXPOSE %s

            CMD ["npm","start","--","-H","0.0.0.0"]
            """.formatted(service.getPort());
        }

        // 🔥 FIX 3: ensure CMD binds to 0.0.0.0
        if (!dockerfile.contains("0.0.0.0")) {
            dockerfile = dockerfile.replaceAll(
                    "CMD \\[(.*?)\\]",
                    "CMD [\"npm\",\"start\",\"--\",\"-H\",\"0.0.0.0\"]"
            );
        }

        return dockerfile;
    }

    private void validate(String dockerfile) {

        if (dockerfile == null || dockerfile.isBlank()) {
            throw new RuntimeException("Empty Dockerfile from Stellar");
        }

        if (!dockerfile.contains("FROM")) {
            throw new RuntimeException("Invalid Dockerfile: missing FROM");
        }

        if (!dockerfile.contains("WORKDIR")) {
            throw new RuntimeException("Invalid Dockerfile: missing WORKDIR");
        }

        if (!dockerfile.contains("COPY")) {
            throw new RuntimeException("Invalid Dockerfile: missing COPY");
        }

        if (!dockerfile.contains("EXPOSE")) {
            throw new RuntimeException("Invalid Dockerfile: missing EXPOSE");
        }

        if (!dockerfile.contains("CMD")) {
            throw new RuntimeException("Invalid Dockerfile: missing CMD");
        }

        // 🔥 Node validation
        if (dockerfile.toLowerCase().contains("node")) {
            if (!dockerfile.contains("npm install")) {
                throw new RuntimeException("Invalid Dockerfile: missing npm install");
            }
        }
    }
}