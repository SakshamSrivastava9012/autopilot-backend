package com.autopilot.service.infrastructure;

import com.autopilot.entity.Deployment;
import com.autopilot.dto.DeployedService;
import com.autopilot.dto.DeploymentManifest;
import com.autopilot.dto.RouteDescriptor;
import com.autopilot.dto.ServiceDescriptor;
import com.autopilot.dto.ServiceRole;
import com.autopilot.analyzer.runtime.RoutingContract;
import com.autopilot.analyzer.runtime.AssetContract;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class UniversalNginxGenerator {

    public String generate(List<Deployment> deployments) {
        StringBuilder config = new StringBuilder();

        config.append("server {\n");
        config.append("    listen 80;\n");
        config.append("    server_name _;\n\n");
        config.append("    client_max_body_size 100M;\n");
        config.append("    gzip on;\n");
        config.append("    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;\n");
        config.append("    proxy_buffering on;\n");
        config.append("    proxy_buffer_size 128k;\n");
        config.append("    proxy_buffers 4 256k;\n");
        config.append("    proxy_busy_buffers_size 256k;\n\n");

        config.append("    location /health {\n");
        config.append("        return 200 'ok';\n");
        config.append("        add_header Content-Type text/plain;\n");
        config.append("    }\n\n");

        for (Deployment d : deployments) {
            if (d == null) continue;
            DeploymentManifest manifest = parseDeploymentManifest(d.getDeployedServicesJson());
            if (manifest != null && manifest.getRoutes() != null) {
                validateManifest(manifest);

                for (RouteDescriptor route : manifest.getRoutes()) {
                    generateRouteLocationBlock(config, route);
                }

                verifyNginxConfig(manifest, config.toString());
            }
        }

        config.append("    location / {\n");
        config.append("        return 404;\n");
        config.append("    }\n");
        config.append("}\n");

        return config.toString();
    }

    public static DeploymentManifest parseDeploymentManifest(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("{")) {
                java.util.Map<String, Object> map = mapper.readValue(trimmed,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                if (map.containsKey("routes") || map.containsKey("services")) {
                    return mapper.readValue(trimmed, DeploymentManifest.class);
                }
                if (map.containsKey("deployedServices")) {
                    Object rawList = map.get("deployedServices");
                    String innerJson = mapper.writeValueAsString(rawList);
                    List<DeployedService> legacyList = mapper.readValue(innerJson,
                            new com.fasterxml.jackson.core.type.TypeReference<List<DeployedService>>() {});
                    return convertLegacyToManifest(legacyList);
                }
            } else if (trimmed.startsWith("[")) {
                List<DeployedService> legacyList = mapper.readValue(trimmed,
                        new com.fasterxml.jackson.core.type.TypeReference<List<DeployedService>>() {});
                return convertLegacyToManifest(legacyList);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static DeploymentManifest convertLegacyToManifest(List<DeployedService> legacyList) {
        java.util.List<ServiceDescriptor> services = new java.util.ArrayList<>();
        java.util.List<RouteDescriptor> routes = new java.util.ArrayList<>();

        for (DeployedService ls : legacyList) {
            ServiceRole role = "frontend".equalsIgnoreCase(ls.getRole()) ? ServiceRole.SPA : ServiceRole.API;
            
            String health = ls.getHealthPath();
            if (health == null || health.isBlank()) {
                health = "/";
            }

            services.add(ServiceDescriptor.builder()
                    .id(ls.getName())
                    .name(ls.getName())
                    .framework(ls.getFramework())
                    .language(ls.getLanguage())
                    .role(role)
                    .port(ls.getPort())
                    .routePrefix(ls.getBasePath())
                    .healthEndpoint(health)
                    .build());

            boolean stripPrefix = true;
            if (ls.getAssetContract() != null) {
                stripPrefix = ls.getAssetContract().isRequiresPrefixRewrite();
            }

            routes.add(RouteDescriptor.builder()
                    .path(ls.getBasePath())
                    .targetService(ls.getName())
                    .container(ls.getContainerName())
                    .internalPort(ls.getHostPort())
                    .stripPrefix(stripPrefix)
                    .healthEndpoint(health)
                    .protocol(ls.getProtocol() != null ? ls.getProtocol() : "HTTP")
                    .websocket(false)
                    .timeout(ls.getStartupTimeout() > 0 ? ls.getStartupTimeout() : 60)
                    .build());
        }

        return DeploymentManifest.builder()
                .application("legacy-app")
                .services(services)
                .routes(routes)
                .build();
    }

    private static List<DeployedService> convertManifestToLegacy(DeploymentManifest manifest) {
        java.util.List<DeployedService> legacyList = new java.util.ArrayList<>();
        if (manifest == null || manifest.getServices() == null) {
            return legacyList;
        }
        for (ServiceDescriptor sd : manifest.getServices()) {
            RouteDescriptor rd = null;
            if (manifest.getRoutes() != null) {
                rd = manifest.getRoutes().stream()
                        .filter(r -> r.getTargetService().equals(sd.getId()))
                        .findFirst()
                        .orElse(null);
            }

            int hp = rd != null ? rd.getInternalPort() : sd.getPort();
            String cName = rd != null ? rd.getContainer() : sd.getName();
            String role = sd.getRole() == ServiceRole.SPA || sd.getRole() == ServiceRole.STATIC_SITE ? "frontend" : "backend";

            DeployedService ds = new DeployedService(
                    sd.getName(), sd.getFramework(), sd.getLanguage(), sd.getServiceRoot(), sd.getPort(), hp,
                    sd.getRoutePrefix(), "", role, sd.getBuildCommand(), sd.getStartCommand(), sd.getRuntime(),
                    sd.getHealthEndpoint(), rd != null ? rd.getProtocol() : "HTTP", List.of(200),
                    rd != null ? rd.getTimeout() : 60, 3
            );
            ds.setContainerName(cName);
            legacyList.add(ds);
        }
        return legacyList;
    }

    private void generateRouteLocationBlock(StringBuilder config, RouteDescriptor route) {
        String path = route.getPath().trim();
        if (!path.startsWith("/")) path = "/" + path;
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.isBlank()) path = "/";

        int port = route.getInternalPort();
        boolean strip = route.isStripPrefix();

        config.append("    location ").append(path).append("/ {\n");
        if (strip) {
            config.append("        proxy_pass http://127.0.0.1:").append(port).append("/;\n");
        } else {
            config.append("        proxy_pass http://127.0.0.1:").append(port).append(";\n");
        }

        appendProxyHeaders(config, path);
        config.append("    }\n\n");
    }

    private void appendProxyHeaders(StringBuilder sb, String xForwardedPrefix) {
        sb.append("        proxy_http_version 1.1;\n");
        sb.append("        proxy_set_header Host $host;\n");
        sb.append("        proxy_set_header X-Real-IP $remote_addr;\n");
        sb.append("        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
        sb.append("        proxy_set_header X-Forwarded-Proto $scheme;\n");
        sb.append("        proxy_set_header X-Forwarded-Host $host;\n");
        sb.append("        proxy_set_header X-Forwarded-Port $server_port;\n");
        if (xForwardedPrefix != null) {
            sb.append("        proxy_set_header X-Forwarded-Prefix ").append(xForwardedPrefix).append(";\n");
            sb.append("        proxy_set_header Forwarded \"for=$remote_addr;proto=$scheme;host=$host;by=$server_addr\";\n");
        }
        sb.append("        proxy_set_header Upgrade $http_upgrade;\n");
        sb.append("        proxy_set_header Connection \"upgrade\";\n");
    }

    public void validateManifest(DeploymentManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("DeploymentManifest cannot be null");
        }
        if (manifest.getServices() == null || manifest.getServices().isEmpty()) {
            throw new IllegalArgumentException("DeploymentManifest must have at least one service");
        }
        if (manifest.getRoutes() == null || manifest.getRoutes().isEmpty()) {
            throw new IllegalArgumentException("DeploymentManifest must have at least one route");
        }

        java.util.Set<String> serviceIds = new java.util.HashSet<>();
        for (ServiceDescriptor svc : manifest.getServices()) {
            serviceIds.add(svc.getId());
            if (svc.getHealthEndpoint() == null || svc.getHealthEndpoint().isBlank()) {
                throw new IllegalStateException("Service " + svc.getId() + " does not have a health endpoint");
            }
        }

        java.util.Set<String> routePaths = new java.util.HashSet<>();
        for (RouteDescriptor route : manifest.getRoutes()) {
            if (!serviceIds.contains(route.getTargetService())) {
                throw new IllegalStateException("Route path " + route.getPath() + " references a non-existent service " + route.getTargetService());
            }
            if (route.getInternalPort() <= 0) {
                throw new IllegalStateException("Route path " + route.getPath() + " has an invalid target port: " + route.getInternalPort());
            }
            if (routePaths.contains(route.getPath())) {
                throw new IllegalStateException("Duplicate route prefix path detected: " + route.getPath());
            }
            routePaths.add(route.getPath());
        }

        for (ServiceDescriptor svc : manifest.getServices()) {
            boolean isHttp = svc.getRole() == ServiceRole.SPA
                    || svc.getRole() == ServiceRole.SSR
                    || svc.getRole() == ServiceRole.STATIC_SITE
                    || svc.getRole() == ServiceRole.API
                    || svc.getRole() == ServiceRole.GRAPHQL
                    || svc.getRole() == ServiceRole.WEBSOCKET;
            if (isHttp) {
                boolean hasRoute = manifest.getRoutes().stream()
                        .anyMatch(r -> r.getTargetService().equals(svc.getId()));
                if (!hasRoute) {
                    throw new IllegalStateException("HTTP Service " + svc.getId() + " does not have any registered routes");
                }
            }
        }
    }

    public void verifyNginxConfig(DeploymentManifest manifest, String nginxConfig) {
        if (manifest == null || nginxConfig == null) {
            throw new IllegalArgumentException("Manifest or Nginx config is null");
        }
        for (RouteDescriptor route : manifest.getRoutes()) {
            String path = route.getPath().trim();
            if (!path.startsWith("/")) path = "/" + path;
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.isBlank()) path = "/";

            String expectedLocation = "location " + path + "/";
            if (!nginxConfig.contains(expectedLocation)) {
                throw new IllegalStateException("Verification failed: Nginx configuration is missing location block for route " + path);
            }
        }
    }

    public static List<DeployedService> parseDeployedServices(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("{")) {
                java.util.Map<String, Object> map = mapper.readValue(trimmed,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                if (map.containsKey("routes") || map.containsKey("services")) {
                    DeploymentManifest manifest = mapper.readValue(trimmed, DeploymentManifest.class);
                    return convertManifestToLegacy(manifest);
                }
                if (map.containsKey("deployedServices")) {
                    Object rawList = map.get("deployedServices");
                    String innerJson = mapper.writeValueAsString(rawList);
                    return mapper.readValue(innerJson,
                            new com.fasterxml.jackson.core.type.TypeReference<List<DeployedService>>() {});
                }
            } else if (trimmed.startsWith("[")) {
                return mapper.readValue(trimmed,
                        new com.fasterxml.jackson.core.type.TypeReference<List<DeployedService>>() {});
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }
}
