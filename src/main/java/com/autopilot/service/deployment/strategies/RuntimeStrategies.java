package com.autopilot.service.deployment.strategies;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.RuntimeType;
import org.springframework.stereotype.Component;

public class RuntimeStrategies {

    @Component
    public static class StaticSiteRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.STATIC;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String pmCmd = metadata.getPackageManager().name().toLowerCase();
            String installCmd = pmCmd.equals("none") ? "echo 'no install'" : pmCmd + " install";
            if (pmCmd.equals("yarn")) installCmd = "yarn install";
            else if (pmCmd.equals("pnpm")) installCmd = "pnpm install";
            else if (pmCmd.equals("bun")) installCmd = "bun install";

            String buildCmd = metadata.getBuildCommand() != null ? metadata.getBuildCommand() : "npm run build";
            String outDir = metadata.getOutputDirectory() != null ? metadata.getOutputDirectory() : "dist";

            String basePath = metadata.getBasePath();
            if (basePath == null || basePath.isBlank()) {
                basePath = "/";
            }
            if (!basePath.startsWith("/")) {
                basePath = "/" + basePath;
            }
            String cleanBase = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
            String routingBase = cleanBase + "/";
            String assetsBase = cleanBase + "/assets/";
            String fallbackPath = cleanBase + "/index.html";

            String rewriteRule = "";
            if (cleanBase != null && !cleanBase.isEmpty() && !cleanBase.equals("/")) {
                rewriteRule = "    rewrite ^" + cleanBase + "(/?.*)$ $1 last;\\n";
            }

            String rawNginxConf = "server {\n" +
                    "    listen 80;\n" +
                    rewriteRule +
                    "    location / {\n" +
                    "        root /usr/share/nginx/html;\n" +
                    "        index index.html index.htm;\n" +
                    "        try_files $uri $uri/ /index.html;\n" +
                    "    }\n" +
                    "    location ~* \\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|map|json)$ {\n" +
                    "        root /usr/share/nginx/html;\n" +
                    "        try_files $uri =404;\n" +
                    "    }\n" +
                    "}\n";

            String cleanNginx = rawNginxConf
                    .replace("\r", "")
                    .replace("'", "'\\''")
                    .replace("\n", "\\n");

            return """
                    # Stage 1: Build static assets
                    FROM node:20-alpine AS builder
                    WORKDIR /app
                    COPY package*.json ./
                    RUN %s
                    COPY . .
                    RUN %s

                    # Stage 2: Serve with Nginx
                    FROM nginx:alpine
                    COPY --from=builder /app/%s /usr/share/nginx/html
                    
                    # Nginx Configuration for Single Page Applications (Routing fallback)
                    RUN printf '%s' > /etc/nginx/conf.d/default.conf

                    EXPOSE 80
                    CMD ["nginx", "-g", "daemon off;"]
                    """.formatted(installCmd, buildCmd, outDir, cleanNginx);
        }

        @Override
        public int containerPort(FrameworkMetadata metadata) {
            return 80;
        }
    }


    @Component
    public static class NodeSsrRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.SSR;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String pmCmd = metadata.getPackageManager().name().toLowerCase();
            String installCmd = pmCmd.equals("none") ? "npm install" : pmCmd + " install";
            if (pmCmd.equals("yarn")) installCmd = "yarn install";
            else if (pmCmd.equals("pnpm")) installCmd = "pnpm install";
            else if (pmCmd.equals("bun")) installCmd = "bun install";

            String buildCmd = metadata.getBuildCommand() != null ? metadata.getBuildCommand() : "npm run build";
            String startCmd = metadata.getStartCommand() != null ? metadata.getStartCommand() : "npm start";

            return """
                    FROM node:20-alpine AS builder
                    WORKDIR /app
                    COPY package*.json ./
                    RUN %s
                    COPY . .
                    RUN %s

                    FROM node:20-alpine
                    WORKDIR /app
                    COPY --from=builder /app /app
                    
                    # Run container as a non-root user for security
                    USER node
                    
                    EXPOSE %d
                    CMD %s
                    """.formatted(installCmd, buildCmd, metadata.getPort(), formatStartCommand(startCmd));
        }
    }

    @Component
    public static class NodeServerRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.NODE_SERVER;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String pmCmd = metadata.getPackageManager().name().toLowerCase();
            String installCmd = pmCmd.equals("none") ? "npm install" : pmCmd + " install";
            
            String buildCmd = metadata.getBuildCommand() != null && !metadata.getBuildCommand().isEmpty()
                    ? "RUN " + metadata.getBuildCommand() : "";
            String startCmd = metadata.getStartCommand() != null ? metadata.getStartCommand() : "node index.js";

            return """
                    FROM node:20-alpine
                    WORKDIR /app
                    COPY package*.json ./
                    RUN %s
                    COPY . .
                    %s
                    
                    # Run container as a non-root user
                    USER node
                    
                    EXPOSE %d
                    CMD %s
                    """.formatted(installCmd, buildCmd, metadata.getPort(), formatStartCommand(startCmd));
        }
    }

    @Component
    public static class JavaJarRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.JAVA_JAR;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String version = metadata.getDefaultRuntimeVersion() != null ? metadata.getDefaultRuntimeVersion() : "21";
            String buildCmd = metadata.getBuildCommand() != null ? metadata.getBuildCommand() : "./mvnw clean package -DskipTests";
            String jarPath = "pom.xml".contains("pom.xml") ? "target/*.jar" : "build/libs/*.jar";

            return """
                    # Stage 1: Build jar
                    FROM maven:3.9.9-eclipse-temurin-%s AS builder
                    WORKDIR /build
                    COPY . .
                    RUN chmod +x mvnw gradlew 2>/dev/null || true
                    RUN %s

                    # Stage 2: Run jar
                    FROM eclipse-temurin:%s-jre-alpine
                    WORKDIR /app
                    COPY --from=builder /build/%s app.jar
                    
                    # Create non-root app user
                    RUN addgroup -S appgroup && adduser -S appuser -G appgroup
                    USER appuser
                    
                    EXPOSE %d
                    ENTRYPOINT ["java", "-jar", "app.jar"]
                    """.formatted(version, buildCmd, version, jarPath, metadata.getPort());
        }
    }

    @Component
    public static class PythonRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.PYTHON;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String version = metadata.getDefaultRuntimeVersion() != null ? metadata.getDefaultRuntimeVersion() : "3.10";
            String buildCmd = metadata.getBuildCommand() != null ? metadata.getBuildCommand() : "pip install -r requirements.txt";
            String startCmd = metadata.getStartCommand() != null ? metadata.getStartCommand() : "python app.py";

            return """
                    FROM python:%s-slim
                    WORKDIR /app
                    COPY requirements.txt ./
                    RUN %s
                    COPY . .
                    
                    # Security: run as non-root user
                    RUN groupadd -g 999 appuser && useradd -r -u 999 -g appuser appuser
                    USER appuser
                    
                    EXPOSE %d
                    CMD %s
                    """.formatted(version, buildCmd, metadata.getPort(), formatStartCommand(startCmd));
        }
    }

    @Component
    public static class GoBinaryRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.GO_BINARY;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String version = metadata.getDefaultRuntimeVersion() != null ? metadata.getDefaultRuntimeVersion() : "1.22";
            String buildCmd = metadata.getBuildCommand() != null ? metadata.getBuildCommand() : "go build -o server .";
            String startCmd = metadata.getStartCommand() != null ? metadata.getStartCommand() : "./server";

            return """
                    FROM golang:%s-alpine AS builder
                    WORKDIR /build
                    COPY . .
                    RUN CGO_ENABLED=0 GOOS=linux %s

                    FROM alpine:3.19
                    WORKDIR /app
                    COPY --from=builder /build/server .
                    
                    # Run as non-root user
                    RUN addgroup -S appgroup && adduser -S appuser -G appgroup
                    USER appuser
                    
                    EXPOSE %d
                    CMD ["./server"]
                    """.formatted(version, buildCmd, metadata.getPort());
        }
    }

    @Component
    public static class RustBinaryRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.RUST_BINARY;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String version = metadata.getDefaultRuntimeVersion() != null ? metadata.getDefaultRuntimeVersion() : "1.77";

            return """
                    FROM rust:%s-slim AS builder
                    WORKDIR /build
                    COPY . .
                    RUN cargo build --release

                    FROM debian:bookworm-slim
                    WORKDIR /app
                    COPY --from=builder /build/target/release/app .
                    
                    # Run as non-root
                    RUN groupadd -g 999 appuser && useradd -r -u 999 -g appuser appuser
                    USER appuser
                    
                    EXPOSE %d
                    CMD ["./app"]
                    """.formatted(version, metadata.getPort());
        }
    }

    @Component
    public static class DotNetRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.DOTNET_BINARY;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String version = metadata.getDefaultRuntimeVersion() != null ? metadata.getDefaultRuntimeVersion() : "8.0";

            return """
                    FROM mcr.microsoft.com/dotnet/sdk:%s AS builder
                    WORKDIR /build
                    COPY . .
                    RUN dotnet publish -c Release -o out

                    FROM mcr.microsoft.com/dotnet/aspnet:%s
                    WORKDIR /app
                    COPY --from=builder /build/out .
                    
                    EXPOSE %d
                    ENTRYPOINT ["dotnet", "%s.dll"]
                    """.formatted(version, version, metadata.getPort(), metadata.getName());
        }
    }

    @Component
    public static class PhpRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.PHP;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String version = metadata.getDefaultRuntimeVersion() != null ? metadata.getDefaultRuntimeVersion() : "8.2";
            String startCmd = metadata.getStartCommand() != null ? metadata.getStartCommand() : "php artisan serve";

            return """
                    FROM php:%s-cli-alpine
                    WORKDIR /app
                    COPY --from=composer:latest /usr/bin/composer /usr/bin/composer
                    COPY . .
                    RUN composer install --no-dev --optimize-autoloader
                    
                    EXPOSE %d
                    CMD %s
                    """.formatted(version, metadata.getPort(), formatStartCommand(startCmd));
        }
    }

    @Component
    public static class RubyOnRailsRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.RUBY_ON_RAILS;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String version = metadata.getDefaultRuntimeVersion() != null ? metadata.getDefaultRuntimeVersion() : "3.2";

            return """
                    FROM ruby:%s
                    WORKDIR /app
                    COPY Gemfile Gemfile.lock ./
                    RUN bundle install
                    COPY . .
                    
                    EXPOSE %d
                    CMD ["bundle", "exec", "rails", "server", "-b", "0.0.0.0", "-p", "3000"]
                    """.formatted(version, metadata.getPort());
        }
    }

    @Component
    public static class GenericRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.GENERIC;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            String buildCmd = metadata.getBuildCommand() != null ? metadata.getBuildCommand() : "echo 'no build'";
            String startCmd = metadata.getStartCommand() != null ? metadata.getStartCommand() : "ls -la";

            return """
                    FROM ubuntu:22.04
                    ENV DEBIAN_FRONTEND=noninteractive
                    RUN apt-get update && apt-get install -y \\
                        openjdk-17-jdk maven \\
                        nodejs npm \\
                        python3 python3-pip \\
                        && rm -rf /var/lib/apt/lists/*
                    WORKDIR /app
                    COPY . .
                    RUN %s || echo "Build step failed"
                    EXPOSE %d
                    CMD %s
                    """.formatted(buildCmd, metadata.getPort(), formatStartCommand(startCmd));
        }
    }

    @Component
    public static class DockerRuntimeStrategy implements RuntimeStrategy {
        @Override
        public boolean supports(RuntimeType runtimeType) {
            return runtimeType == RuntimeType.DOCKER;
        }

        @Override
        public String generateDockerfile(FrameworkMetadata metadata) {
            return ""; // Dockerfile already exists, do not overwrite/generate
        }
    }

    private static String formatStartCommand(String startCmd) {
        if (startCmd == null || startCmd.trim().isEmpty()) {
            return "[\"npm\", \"start\"]";
        }
        startCmd = startCmd.trim();
        if (startCmd.startsWith("[") && startCmd.endsWith("]")) {
            return startCmd;
        }
        // Split by space and convert to JSON array format
        String[] parts = startCmd.split("\\s+");
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < parts.length; i++) {
            sb.append("\"").append(parts[i].replace("\"", "\\\"")).append("\"");
            if (i < parts.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
