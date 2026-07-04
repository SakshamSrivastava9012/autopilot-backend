package com.autopilot.service.deployment;

import com.autopilot.analyzer.runtime.*;
import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.service.infrastructure.ec2.SSMDeployService;
import com.autopilot.service.log.DeploymentLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RuntimeInspectorService {

    private final SSMDeployService ssmDeployService;
    private final DeploymentLogService logService;

    public static class InspectionResult {
        public RoutingContract routingContract;
        public AssetContract assetContract;
        public HealthContract healthContract;
        public OAuthContract oauthContract;
        public RuntimeContract runtimeContract;
    }

    public InspectionResult inspect(
            String instanceId,
            String containerName,
            int hostPort,
            String region,
            AwsCredentialsDto creds,
            String framework,
            String did,
            String publicIp,
            String basePath
    ) {
        return inspect(instanceId, containerName, hostPort, region, creds, framework, did, publicIp, basePath, null);
    }

    public InspectionResult inspect(
            String instanceId,
            String containerName,
            int hostPort,
            String region,
            AwsCredentialsDto creds,
            String framework,
            String did,
            String publicIp,
            String basePath,
            com.autopilot.service.deployment.runtime.dependency.RuntimeContainerDescriptor descriptor
    ) {
        if (descriptor != null) {
            String expectedApp = descriptor.applicationContainerName();
            String expectedDb = descriptor.databaseContainerName();
            if (containerName != null && !containerName.contains("redis")) {
                if (!com.autopilot.service.infrastructure.ec2.StatefulWaitEngine.isContainerNameMatching(containerName, expectedApp, expectedDb)) {
                    throw new IllegalStateException("Assertion Failed: Container mismatch! Expected application '" + expectedApp 
                        + "' or database '" + expectedDb + "', but got: '" + containerName + "'");
                }
            }
        }
        logService.info(did, "DEPLOYING", "🔍 Initiating Runtime Inspection on container: " + containerName + " (port: " + hostPort + ")");

        String inspectOut = ssmDeployService.runCommandAndGetOutput(instanceId, "docker inspect " + containerName, region, creds);
        logService.info(did, "DEPLOYING", "✅ Retrieved docker inspect for " + containerName);

        String logsOut = ssmDeployService.runCommandAndGetOutput(instanceId, "docker logs --tail 50 " + containerName, region, creds);
        logService.info(did, "DEPLOYING", "✅ Retrieved container startup logs");

        String envOut = ssmDeployService.runCommandAndGetOutput(instanceId, "docker exec " + containerName + " env 2>/dev/null || true", region, creds);

        String fileLayout = ssmDeployService.runCommandAndGetOutput(instanceId,
                "docker exec " + containerName + " find . -maxdepth 4 -not -path '*/node_modules/*' 2>/dev/null || docker exec " + containerName + " ls -R 2>/dev/null || true",
                region, creds);
        logService.info(did, "DEPLOYING", "✅ Analyzed container filesystem structure");

        // Analyze historyFallback
        String curl404 = ssmDeployService.runCommandAndGetOutput(instanceId,
                "curl -s -i http://127.0.0.1:" + hostPort + "/non-existent-route-for-inspection || true", region, creds);

        boolean historyFallback = false;
        if (curl404 != null && !curl404.contains("ERROR:")) {
            boolean hasOkStatus = curl404.contains("HTTP/1.1 200") || curl404.contains("HTTP/1.1 204") || curl404.contains("HTTP/1.1 302") || curl404.contains("HTTP/1.1 301") || curl404.contains("HTTP/2 200");
            boolean isHtml = curl404.toLowerCase().contains("content-type: text/html");
            if (hasOkStatus && isHtml) {
                historyFallback = true;
            }
        }

        boolean preservesPrefix = false;
        if (framework != null) {
            String lowerFw = framework.toLowerCase();
            if (lowerFw.contains("next") || lowerFw.contains("nuxt") || lowerFw.contains("ssr") || lowerFw.contains("spring") || lowerFw.contains("quarkus") || lowerFw.contains("boot")) {
                preservesPrefix = true;
            }
        }

        // File layout analysis
        List<String> staticDirs = new ArrayList<>();
        List<String> publicDirs = new ArrayList<>();
        List<String> immutablePrefixes = new ArrayList<>();
        Set<String> fileExtensions = new HashSet<>(Set.of("js", "css", "html", "png", "jpg", "jpeg", "svg", "ico", "woff", "woff2", "ttf"));

        if (fileLayout != null && !fileLayout.isBlank()) {
            String[] lines = fileLayout.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains(".next") || trimmed.contains("_next")) {
                    if (!immutablePrefixes.contains("/_next/")) {
                        immutablePrefixes.add("/_next/");
                    }
                }
                if (trimmed.contains("/assets/") || trimmed.endsWith("/assets") || trimmed.contains("/static/") || trimmed.endsWith("/static")) {
                    if (trimmed.contains("assets") && !immutablePrefixes.contains("/assets/")) {
                        immutablePrefixes.add("/assets/");
                    }
                    if (trimmed.contains("static") && !immutablePrefixes.contains("/static/")) {
                        immutablePrefixes.add("/static/");
                    }
                }
                if (trimmed.contains("/public/") || trimmed.endsWith("/public")) {
                    if (!publicDirs.contains("public")) {
                        publicDirs.add("public");
                    }
                }
                if (trimmed.contains("/dist/") || trimmed.endsWith("/dist") || trimmed.contains("/build/") || trimmed.endsWith("/build")) {
                    String d = trimmed.contains("dist") ? "dist" : "build";
                    if (!staticDirs.contains(d)) {
                        staticDirs.add(d);
                    }
                }
            }
        }

        boolean isBackend = framework != null && (framework.toLowerCase().contains("spring") || framework.toLowerCase().contains("boot") || framework.toLowerCase().contains("quarkus"));
        if (!isBackend) {
            if (immutablePrefixes.isEmpty()) {
                immutablePrefixes.add("/assets/");
                immutablePrefixes.add("/static/");
            }
            if (staticDirs.isEmpty()) {
                staticDirs.add("dist");
                staticDirs.add("build");
            }
            if (publicDirs.isEmpty()) {
                publicDirs.add("public");
            }
        }

        // Build core Contracts
        RoutingContract routing = RoutingContract.builder()
                .historyFallback(historyFallback)
                .preservesPrefix(preservesPrefix)
                .fallbackRedirectPath("/index.html")
                .staticAssetsPrefix("/assets/")
                .backendPrefixes(isBackend ? List.of("/api/", "/actuator/") : List.of())
                .build();

        AssetContract assets = AssetContract.builder()
                .staticDirectories(staticDirs)
                .publicDirectories(publicDirs)
                .immutableAssetPrefixes(immutablePrefixes)
                .requiresPrefixRewrite(!preservesPrefix)
                .fileExtensions(fileExtensions)
                .build();

        // 1. Health Contract
        String checkPath = isBackend ? "/actuator/health" : "/";
        HealthContract health = HealthContract.builder()
                .checkPath(checkPath)
                .expectedStatusCodes(Set.of(200, 204, 301, 302, 404))
                .expectedMimeTypes(new HashSet<>(Set.of("application/json", "text/html", "text/plain")))
                .build();

        // 2. OAuth Contract
        boolean supportsOAuth = envOut != null && (envOut.contains("OAUTH") || envOut.contains("oauth2") || envOut.contains("security"));
        OAuthContract oauth = OAuthContract.builder()
                .supportsOAuth(supportsOAuth)
                .callbackUrlPath("/login/oauth2/code/")
                .oauthPrefixes(List.of("/login", "/logout", "/oauth2"))
                .build();

        // 3. Discover capabilities dynamically from running process and files
        String psOut = ssmDeployService.runCommandAndGetOutput(instanceId,
                "docker exec " + containerName + " ps aux 2>/dev/null || docker exec " + containerName + " ps -ef 2>/dev/null || true",
                region, creds);

        boolean hasJavaProcess = (psOut != null && psOut.contains("java"))
                || (logsOut != null && (logsOut.contains("Spring Boot") || logsOut.contains("spring-boot") || logsOut.contains("JVM") || logsOut.contains("Java")));
        boolean hasNodeProcess = (psOut != null && psOut.contains("node"))
                || (logsOut != null && (logsOut.contains("next") || logsOut.contains("node") || logsOut.contains("express") || logsOut.contains("nest")));

        String packageJsonContent = ssmDeployService.runCommandAndGetOutput(instanceId,
                "docker exec " + containerName + " cat package.json 2>/dev/null || docker exec " + containerName + " cat app/package.json 2>/dev/null || true",
                region, creds);

        boolean hasNextJs = (packageJsonContent != null && packageJsonContent.contains("\"next\""))
                || (fileLayout != null && (fileLayout.contains(".next") || fileLayout.contains("next.config")));

        Set<CapabilityType> types = new HashSet<>();

        if (hasJavaProcess || (framework != null && (framework.toLowerCase().contains("spring") || framework.toLowerCase().contains("boot") || framework.toLowerCase().contains("quarkus")))) {
            types.add(CapabilityType.NODE_SERVER);
            types.add(CapabilityType.BASE_PATH_REQUIRED);
        } else if (hasNodeProcess || hasNextJs) {
            if (hasNextJs) {
                types.add(CapabilityType.SSR);
                types.add(CapabilityType.HYBRID);
                types.add(CapabilityType.STATIC_ASSETS);
                types.add(CapabilityType.BASE_PATH_REQUIRED);
            } else {
                types.add(CapabilityType.NODE_SERVER);
                types.add(CapabilityType.BASE_PATH_REQUIRED);
            }
        } else {
            types.add(CapabilityType.STATIC_ASSETS);
            if (historyFallback) {
                types.add(CapabilityType.SPA);
                types.add(CapabilityType.HISTORY_FALLBACK);
            } else {
                types.add(CapabilityType.STATIC_SITE);
            }
        }

        if (preservesPrefix) {
            types.add(CapabilityType.PREFIX_REWRITE_SUPPORTED);
        }

        RuntimeCapabilities capabilities = RuntimeCapabilities.builder().types(types).build();

        String serviceAccessUrl = "http://" + publicIp + basePath;
        if (!serviceAccessUrl.endsWith("/")) {
            serviceAccessUrl += "/";
        }

        RuntimeContract runtimeContract = RuntimeContract.builder()
                .routing(routing)
                .assets(assets)
                .health(health)
                .oauth(oauth)
                .capabilities(capabilities)
                .externalBrowserUrl(serviceAccessUrl)
                .build();

        InspectionResult res = new InspectionResult();
        res.routingContract = routing;
        res.assetContract = assets;
        res.healthContract = health;
        res.oauthContract = oauth;
        res.runtimeContract = runtimeContract;

        logService.info(did, "DEPLOYING", "✅ Inspection COMPLETE: preservesPrefix=" + preservesPrefix + ", historyFallback=" + historyFallback + ", assets=" + immutablePrefixes + ", capabilities=" + types);
        return res;
    }
}
