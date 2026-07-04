package com.autopilot.service.deployment.validation;

import com.autopilot.dto.DeployedService;
import com.autopilot.dto.DeploymentManifest;
import com.autopilot.dto.RouteDescriptor;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Set;

@Component
public class CapabilityHealthVerifier {

    private static final Set<Integer> ALLOWED_STATUS_CODES = Set.of(
            200, 201, 202, 204, 301, 302, 307, 308, 401, 403, 404, 405
    );

    public boolean verifyHealth(DeployedService ds, String publicIp, DeploymentManifest manifest) {
        // Find matching RouteDescriptor from DeploymentManifest
        RouteDescriptor route = null;
        if (manifest != null && manifest.getRoutes() != null) {
            route = manifest.getRoutes().stream()
                    .filter(r -> r.getPath().equals(ds.getBasePath()))
                    .findFirst()
                    .orElse(null);
        }

        String protocol = "http";
        String routePath = ds.getBasePath();
        String healthPath = ds.getHealthPath();

        if (route != null) {
            if (route.getProtocol() != null) {
                protocol = route.getProtocol().toLowerCase();
            }
            if (route.getPath() != null) {
                routePath = route.getPath();
            }
            if (route.getHealthEndpoint() != null) {
                healthPath = route.getHealthEndpoint();
            }
        }

        if (healthPath == null || healthPath.isBlank()) {
            healthPath = "/";
        }
        if (!healthPath.startsWith("/")) {
            healthPath = "/" + healthPath;
        }
        if (routePath == null) {
            routePath = "/";
        }

        String url = protocol + "://" + publicIp + (routePath + "/" + healthPath).replaceAll("/+", "/");
        System.out.println("🔍 Verifying service health at URL: " + url);

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            System.out.println("📡 Health check response code: " + code + " for URL: " + url);

            return ALLOWED_STATUS_CODES.contains(code);
        } catch (Exception e) {
            System.err.println("❌ Health check failed for URL: " + url + " - Error: " + e.getMessage());
            return false;
        }
    }
}
