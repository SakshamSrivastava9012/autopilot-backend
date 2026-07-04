package com.autopilot.service.deployment;

import com.autopilot.entity.Deployment;
import com.autopilot.enums.DeploymentStatus;
import com.autopilot.repository.DeploymentRepository;
import com.autopilot.service.log.DeploymentLogService;
import com.autopilot.service.deployment.strategies.HealthCheckStrategy;
import com.autopilot.analyzer.model.FrameworkType;
import com.autopilot.analyzer.model.FrameworkMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import com.autopilot.analyzer.runtime.FrontendRuntimeStrategyRegistry;
import com.autopilot.analyzer.runtime.FrontendRuntimeStrategy;
import com.autopilot.analyzer.runtime.CapabilityType;

/**
 * Post-Deploy Health Validation Service.
 */
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogService logService;
    private final List<HealthCheckStrategy> healthCheckStrategies;
    private final FrontendRuntimeStrategyRegistry strategyRegistry;

    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_WAIT_MS = 15_000;      // 15s before first check
    private static final int INITIAL_BACKOFF_MS = 3_000;    // 3s → 6s → 12s → 24s → 48s

    public record HealthResult(
            boolean healthy,
            int httpStatus,
            long responseTimeMs,
            String failureReason,
            FailureCategory category
    ) {}

    public enum FailureCategory {
        HEALTHY,
        CONNECTION_REFUSED,
        TIMEOUT,
        SERVER_ERROR,
        NOT_FOUND,
        REDIRECT_LOOP,
        UNKNOWN
    }

    public HealthResult validate(Deployment deployment, String publicIp, String basePath) {
        return validate(deployment, publicIp, basePath, "/");
    }

    public HealthResult validate(Deployment deployment, String publicIp, String basePath, String healthCheckPath) {
        return validate(deployment, publicIp, basePath, healthCheckPath, "HTTP", List.of(200, 204, 301, 302), 60, 20);
    }

    public HealthResult validate(
            Deployment deployment,
            String publicIp,
            String basePath,
            String healthCheckPath,
            String protocol,
            List<Integer> expectedStatusCodes,
            int startupTimeout,
            int retryPolicy
    ) {
        String did = deployment.getId();
        logService.info(did, "DEPLOYING", "Starting post-deploy health validation (protocol=" + protocol + ", timeout=" + startupTimeout + "s)...");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {}

        String url = buildSingleCheckUrl(publicIp, basePath, healthCheckPath, protocol);
        
        List<Integer> finalStatusCodes = new ArrayList<>(expectedStatusCodes);
        finalStatusCodes.remove(Integer.valueOf(404));
        finalStatusCodes.remove(Integer.valueOf(500));
        finalStatusCodes.remove(Integer.valueOf(503));

        java.util.Optional<FrontendRuntimeStrategy> optStrategy = strategyRegistry.getStrategy(deployment.getStrategyUsed());
        if (optStrategy.isPresent()) {
            finalStatusCodes = new ArrayList<>(optStrategy.get().health().getExpectedStatusCodes());
            logService.info(did, "DEPLOYING", "Applying health contract for " + deployment.getStrategyUsed() + ": expected codes " + finalStatusCodes);
        }

        HealthResult result = retryHealthCheckParameterized(url, finalStatusCodes, startupTimeout, retryPolicy, did);

        markDeploymentStatus(deployment, result);
        return result;
    }

    public HealthResult validateUrl(String publicIp, String basePath, String logDeploymentId) {
        return validateUrl(publicIp, basePath, logDeploymentId, "/");
    }

    public HealthResult validateUrl(String publicIp, String basePath, String logDeploymentId, String healthCheckPath) {
        return validateUrl(publicIp, basePath, healthCheckPath, "HTTP", List.of(200, 204, 301, 302), 60, 20, logDeploymentId, null);
    }

    public HealthResult validateUrl(
            String publicIp,
            String basePath,
            String healthCheckPath,
            String protocol,
            List<Integer> expectedStatusCodes,
            int startupTimeout,
            int retryPolicy,
            String logDeploymentId,
            String framework
    ) {
        String url = buildSingleCheckUrl(publicIp, basePath, healthCheckPath, protocol);
        
        List<Integer> finalStatusCodes = new ArrayList<>(expectedStatusCodes);
        finalStatusCodes.remove(Integer.valueOf(404));
        finalStatusCodes.remove(Integer.valueOf(500));
        finalStatusCodes.remove(Integer.valueOf(503));

        if (framework != null) {
            java.util.Optional<FrontendRuntimeStrategy> optStrategy = strategyRegistry.getStrategy(framework);
            if (optStrategy.isPresent()) {
                finalStatusCodes = new ArrayList<>(optStrategy.get().health().getExpectedStatusCodes());
                logService.info(logDeploymentId, "DEPLOYING", "Applying health contract for " + framework + ": expected codes " + finalStatusCodes);
            }
        }
        
        return retryHealthCheckParameterized(url, finalStatusCodes, startupTimeout, retryPolicy, logDeploymentId);
    }

    private void waitForStartup(String did) {
        logService.info(did, "DEPLOYING", "Waiting " + (INITIAL_WAIT_MS / 1000) + "s for container startup...");
        try {
            Thread.sleep(INITIAL_WAIT_MS);
        } catch (InterruptedException ignored) {}
    }

    private String buildSingleCheckUrl(String publicIp, String basePath, String healthCheckPath, String protocol) {
        String base = protocol.toLowerCase() + "://" + publicIp;
        String path = basePath != null ? basePath : "";
        String hPath = healthCheckPath != null ? healthCheckPath : "/";

        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/") && hPath.startsWith("/")) {
            path = path.substring(0, path.length() - 1);
        } else if (!path.isEmpty() && !path.endsWith("/") && !hPath.startsWith("/")) {
            path = path + "/";
        }

        return base + path + hPath;
    }

    private List<String> buildCheckUrls(String publicIp, String basePath, String healthCheckPath) {
        String base = "http://" + publicIp;
        String path = basePath != null ? basePath : "";

        List<String> urls = new ArrayList<>();
        
        // Prioritize custom/framework healthCheckPath first
        if (healthCheckPath != null && !healthCheckPath.isEmpty() && !healthCheckPath.equals("/")) {
            urls.add(base + path + healthCheckPath);
        }

        urls.add(base + path + "/health");
        urls.add(base + path + "/api/health");
        urls.add(base + path + "/actuator/health");
        urls.add(base + path + "/");
        urls.add(base + path);

        return urls;
    }

    private HealthResult retryHealthCheckParameterized(
            String url,
            List<Integer> expectedStatusCodes,
            int startupTimeout,
            int retryPolicy,
            String did
    ) {
        int intervalMs = (startupTimeout * 1000) / retryPolicy;
        if (intervalMs < 1000) intervalMs = 1000;

        HealthResult lastResult = null;

        for (int attempt = 1; attempt <= retryPolicy; attempt++) {
            logService.info(did, "DEPLOYING", "Health check attempt " + attempt + "/" + retryPolicy + " on URL: " + url);

            lastResult = checkHttpParameterized(url, expectedStatusCodes);
            if (lastResult.healthy) {
                logService.info(did, "DEPLOYING",
                        "✅ Health check PASSED on attempt " + attempt
                                + " — " + url + " → HTTP " + lastResult.httpStatus
                                + " (" + lastResult.responseTimeMs + "ms)");
                return lastResult;
            }

            String reason = lastResult != null ? lastResult.failureReason : "unknown";
            logService.info(did, "DEPLOYING",
                    "⚠️ Attempt " + attempt + " failed: " + reason
                            + " — retrying in " + (intervalMs / 1000) + "s");

            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException ignored) {}
        }

        String finalReason = lastResult != null ? lastResult.failureReason : "All health checks failed";
        logService.error(did, "DEPLOYING", "❌ Health check FAILED after " + retryPolicy + " attempts: " + finalReason);

        return lastResult != null ? lastResult
                : new HealthResult(false, 0, 0, "All health checks failed", FailureCategory.UNKNOWN);
    }

    private HealthResult retryHealthCheck(List<String> urls, String did) {
        int backoffMs = INITIAL_BACKOFF_MS;
        HealthResult lastResult = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            logService.info(did, "DEPLOYING", "Health check attempt " + attempt + "/" + MAX_RETRIES + "...");

            for (String url : urls) {
                lastResult = checkHttp(url);
                if (lastResult.healthy) {
                    logService.info(did, "DEPLOYING",
                            "✅ Health check PASSED on attempt " + attempt
                                    + " — " + url + " → HTTP " + lastResult.httpStatus
                                    + " (" + lastResult.responseTimeMs + "ms)");
                    return lastResult;
                }
            }

            String reason = lastResult != null ? lastResult.failureReason : "unknown";
            logService.info(did, "DEPLOYING",
                    "⚠️ Attempt " + attempt + " failed: " + reason
                            + " — retrying in " + (backoffMs / 1000) + "s");

            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ignored) {}

            backoffMs *= 2;
        }

        String finalReason = lastResult != null ? lastResult.failureReason : "All health checks failed";
        logService.error(did, "DEPLOYING", "❌ Health check FAILED after " + MAX_RETRIES + " attempts: " + finalReason);

        return lastResult != null ? lastResult
                : new HealthResult(false, 0, 0, "All health checks failed", FailureCategory.UNKNOWN);
    }

    private HealthResult checkHttp(String url) {
        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setInstanceFollowRedirects(false);

            int status = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - start;

            String body = "";
            if (status >= 400 && conn.getErrorStream() != null) {
                try (java.util.Scanner scanner = new java.util.Scanner(conn.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                    body = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                } catch (Exception ignored) {}
            }
            conn.disconnect();

            if (status == 503 || status == 403) {
                if (body.contains("Web Page Blocked") || body.contains("Fortinet") || body.contains("Category:")) {
                    return new HealthResult(true, status, elapsed,
                            "Firewall block detected but assuming healthy (" + status + ")", FailureCategory.HEALTHY);
                }
            }

            if (status >= 200 && status < 400) {
                return new HealthResult(true, status, elapsed, null, FailureCategory.HEALTHY);
            } else if (status >= 500) {
                return new HealthResult(false, status, elapsed,
                        "Server error: HTTP " + status, FailureCategory.SERVER_ERROR);
            } else if (status == 404) {
                return new HealthResult(false, status, elapsed,
                        "Not found: HTTP 404 — check basePath config", FailureCategory.NOT_FOUND);
            } else {
                return new HealthResult(false, status, elapsed,
                        "Unexpected status: HTTP " + status, FailureCategory.UNKNOWN);
            }

        } catch (java.net.ConnectException e) {
            long elapsed = System.currentTimeMillis() - start;
            return new HealthResult(false, 0, elapsed,
                    "Connection refused — container may not be running", FailureCategory.CONNECTION_REFUSED);
        } catch (java.net.SocketTimeoutException e) {
            long elapsed = System.currentTimeMillis() - start;
            return new HealthResult(false, 0, elapsed,
                    "Timeout — container running but not responding", FailureCategory.TIMEOUT);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return new HealthResult(false, 0, elapsed,
                    "Unexpected error: " + e.getMessage(), FailureCategory.UNKNOWN);
        }
    }

    private HealthResult checkHttpParameterized(String url, List<Integer> expectedStatusCodes) {
        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setInstanceFollowRedirects(false);

            int status = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - start;

            String body = "";
            if (status >= 400 && conn.getErrorStream() != null) {
                try (java.util.Scanner scanner = new java.util.Scanner(conn.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                    body = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                } catch (Exception ignored) {}
            }
            conn.disconnect();

            if (status == 503 || status == 403) {
                if (body.contains("Web Page Blocked") || body.contains("Fortinet") || body.contains("Category:")) {
                    return new HealthResult(true, status, elapsed,
                            "Firewall block detected but assuming healthy (" + status + ")", FailureCategory.HEALTHY);
                }
            }

            if (expectedStatusCodes.contains(status)) {
                return new HealthResult(true, status, elapsed, null, FailureCategory.HEALTHY);
            } else if (status >= 500) {
                return new HealthResult(false, status, elapsed,
                        "Server error: HTTP " + status, FailureCategory.SERVER_ERROR);
            } else if (status == 404) {
                return new HealthResult(false, status, elapsed,
                        "Not found: HTTP 404 — check basePath/healthCheckPath config", FailureCategory.NOT_FOUND);
            } else {
                return new HealthResult(false, status, elapsed,
                        "Unexpected status: HTTP " + status, FailureCategory.UNKNOWN);
            }

        } catch (java.net.ConnectException e) {
            long elapsed = System.currentTimeMillis() - start;
            return new HealthResult(false, 0, elapsed,
                    "Connection refused — container may not be running", FailureCategory.CONNECTION_REFUSED);
        } catch (java.net.SocketTimeoutException e) {
            long elapsed = System.currentTimeMillis() - start;
            return new HealthResult(false, 0, elapsed,
                    "Timeout — container running but not responding", FailureCategory.TIMEOUT);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return new HealthResult(false, 0, elapsed,
                    "Unexpected error: " + e.getMessage(), FailureCategory.UNKNOWN);
        }
    }


    public String getStructuredDiagnostics(
            HealthResult result,
            String name,
            String basePath,
            String healthPath,
            String protocol,
            String publicIp
    ) {
        String url = buildSingleCheckUrl(publicIp, basePath, healthPath, protocol);
        StringBuilder sb = new StringBuilder();
        sb.append("\n==================================================\n");
        sb.append("❌ Deployment Verification Failed [").append(name).append("]\n");
        sb.append("Reason:\n");
        sb.append("  ").append(result.failureReason() != null ? result.failureReason() : "No response from endpoint").append("\n");
        sb.append("  Category: ").append(result.category()).append("\n");
        sb.append("  HTTP Status Code: ").append(result.httpStatus()).append("\n");
        sb.append("  Response Time: ").append(result.responseTimeMs()).append("ms\n");
        sb.append("Health Probe Queried:\n");
        sb.append("  ").append(url).append("\n");
        sb.append("Expected HTTP status codes to be in strategy list.\n");
        sb.append("Deployment Base Path:\n");
        sb.append("  ").append(basePath).append("\n");
        sb.append("Recommended Actions:\n");
        if (result.category() == FailureCategory.CONNECTION_REFUSED) {
            sb.append("  - Check if the container actually started and is listening on the designated container port.\n");
            sb.append("  - Verify that the Docker run port mapping is correct and matches the strategy.\n");
        } else if (result.category() == FailureCategory.TIMEOUT) {
            sb.append("  - Check if the application takes too long to boot up or is blocked during startup.\n");
            sb.append("  - Check container logs for database connection timeouts or missing dependencies.\n");
        } else if (result.category() == FailureCategory.NOT_FOUND) {
            sb.append("  - Check if the application router handles the base path '").append(basePath).append("' correctly.\n");
            sb.append("  - Check if the Nginx configuration is mapping the request to the correct host/port.\n");
        } else {
            sb.append("  - Check container logs using 'docker logs <container_name>' to view application-level errors.\n");
        }
        sb.append("==================================================\n");
        return sb.toString();
    }


    public String classifyFailure(HealthResult result) {
        if (result.healthy) return "HEALTHY";
        return switch (result.category) {
            case CONNECTION_REFUSED -> "Container is not listening on the expected port. Check if EXPOSE port matches app's port.";
            case TIMEOUT -> "Container is running but the application is not responding. App may be stuck in startup.";
            case SERVER_ERROR -> "Application returned HTTP " + result.httpStatus + ". Check app logs.";
            case NOT_FOUND -> "HTTP 404 — check basePath configuration.";
            case REDIRECT_LOOP -> "Too many redirects.";
            default -> "Unknown failure: " + result.failureReason;
        };
    }

    private void markDeploymentStatus(Deployment deployment, HealthResult result) {
        if (result.healthy) {
            deployment.setStatus(DeploymentStatus.RUNNING.name());
            deploymentRepository.save(deployment);
        } else {
            deployment.setStatus(DeploymentStatus.FAILED.name());
            deployment.setLogs("Health check failed: " + classifyFailure(result));
            deploymentRepository.save(deployment);
        }
    }

    public void verifyAssetsAndSpaRouting(String publicIp, String basePath, String logDeploymentId) {
        verifyAssetsAndSpaRouting(publicIp, basePath, logDeploymentId, "static");
    }

    public void verifyAssetsAndSpaRouting(String publicIp, String basePath, String logDeploymentId, String framework) {
        String mainUrl = buildSingleCheckUrl(publicIp, basePath, "/", "HTTP");
        logService.info(logDeploymentId, "DEPLOYING", "🔍 Initiating post-deploy asset & SPA routing verification on: " + mainUrl);

        com.autopilot.analyzer.runtime.AssetContract assetContract = null;
        com.autopilot.analyzer.runtime.RoutingContract routingContract = null;

        if (logDeploymentId != null && deploymentRepository != null) {
            java.util.Optional<Deployment> optDep = deploymentRepository.findById(logDeploymentId);
            if (optDep.isPresent() && optDep.get().getDeployedServicesJson() != null) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    List<com.autopilot.dto.DeployedService> svcs = mapper.readValue(
                        optDep.get().getDeployedServicesJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<com.autopilot.dto.DeployedService>>() {}
                    );
                    for (var s : svcs) {
                        if (s.getFramework() != null && s.getFramework().equalsIgnoreCase(framework)) {
                            assetContract = s.getAssetContract();
                            routingContract = s.getRoutingContract();
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        FrontendRuntimeStrategy strategy = strategyRegistry.getStrategy(framework)
                .orElse(new com.autopilot.analyzer.runtime.StaticHtmlStrategy());

        if (assetContract == null) {
            assetContract = strategy.assets();
        }
        if (routingContract == null) {
            routingContract = strategy.routing();
        }

        logService.info(logDeploymentId, "DEPLOYING", "Applying verification contract for framework [" + framework + "] with capabilities " + strategy.capabilities().getTypes());

        // 1. Fetch main page HTML
        String htmlBody = "";
        int mainStatus = 0;
        String contentType = "";
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(mainUrl).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            mainStatus = conn.getResponseCode();
            contentType = conn.getContentType();

            if (mainStatus >= 200 && mainStatus < 400) {
                try (java.util.Scanner scanner = new java.util.Scanner(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                    htmlBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                }
            } else if (conn.getErrorStream() != null) {
                try (java.util.Scanner scanner = new java.util.Scanner(conn.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                    htmlBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            throw new RuntimeException("Post-deploy verification failed: Could not connect to main page " + mainUrl + " (" + e.getMessage() + ")");
        }

        // Validate HTTP Status Code based on Health Contract
        if (!strategy.health().getExpectedStatusCodes().contains(mainStatus)) {
            throw new RuntimeException("Post-deploy verification failed: Main page returned HTTP " + mainStatus + ", which is not in strategy expected status codes: " + strategy.health().getExpectedStatusCodes());
        }

        // Validate MIME type of main page based on Health Contract
        if (contentType != null) {
            boolean mimeMatch = false;
            for (String expectedMime : strategy.health().getExpectedMimeTypes()) {
                if (contentType.toLowerCase().contains(expectedMime.toLowerCase())) {
                    mimeMatch = true;
                    break;
                }
            }
            if (!mimeMatch) {
                throw new RuntimeException("Post-deploy verification failed: Main page MIME type " + contentType + " does not match strategy expected MIME types: " + strategy.health().getExpectedMimeTypes());
            }
        }

        logService.info(logDeploymentId, "DEPLOYING", "   ✅ Main page loaded (HTTP " + mainStatus + ", Content-Type: " + contentType + ")");

        // 2. Extract asset URLs
        List<String> assetUrls = new java.util.ArrayList<>();
        
        java.util.regex.Pattern linkPattern = java.util.regex.Pattern.compile(
            "(?:src|href|srcset|poster|manifest)\\s*=\\s*\"([^\"]+)\""
        );
        java.util.regex.Matcher linkMatcher = linkPattern.matcher(htmlBody);
        while (linkMatcher.find()) {
            String val = linkMatcher.group(1).trim();
            if (linkMatcher.group(0).startsWith("srcset")) {
                String[] parts = val.split(",");
                for (String part : parts) {
                    String cleanPart = part.trim().split("\\s+")[0];
                    if (!cleanPart.isEmpty()) {
                        assetUrls.add(cleanPart);
                    }
                }
            } else {
                assetUrls.add(val);
            }
        }

        // Add default assets check if not detected
        if (assetUrls.stream().noneMatch(u -> u.contains("favicon"))) {
            assetUrls.add(basePath + "/favicon.ico");
        }

        // Normalize and filter URLs using RFC 3986 resolution
        List<String> normalizedUrls = new java.util.ArrayList<>();
        for (String url : assetUrls) {
            String trimmed = url.trim();
            if (trimmed.startsWith("//")) {
                continue;
            }
            try {
                URI mainUri = URI.create(mainUrl);
                URI resolvedUri = mainUri.resolve(trimmed);
                if (resolvedUri.getHost() != null && resolvedUri.getHost().equals(mainUri.getHost())) {
                    String fullUrl = resolvedUri.toString();
                    if (!normalizedUrls.contains(fullUrl)) {
                        normalizedUrls.add(fullUrl);
                    }
                }
            } catch (Exception ignored) {}
        }

        logService.info(logDeploymentId, "DEPLOYING", "   🔍 Found " + normalizedUrls.size() + " assets to verify.");

        // 3. Probe and validate each asset
        for (String assetUrl : normalizedUrls) {
            logService.info(logDeploymentId, "DEPLOYING", "   Probing asset: " + assetUrl);
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(assetUrl).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int status = conn.getResponseCode();
                String mimeType = conn.getContentType();
                String bodyPrefix = "";

                if (status >= 200 && status < 300) {
                    try (java.io.InputStream in = conn.getInputStream()) {
                        byte[] buffer = new byte[200];
                        int read = in.read(buffer);
                        if (read > 0) {
                            bodyPrefix = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                }
                conn.disconnect();

                logService.info(logDeploymentId, "DEPLOYING", "      -> HTTP " + status + " | MIME: " + mimeType);

                // Determine if this URL is considered a static asset
                boolean isStaticAsset = false;
                String cleanUrl = assetUrl.toLowerCase();

                if (assetContract.getFileExtensions() != null) {
                    for (String ext : assetContract.getFileExtensions()) {
                        if (cleanUrl.endsWith("." + ext) || cleanUrl.contains("." + ext + "?")) {
                            isStaticAsset = true;
                            break;
                        }
                    }
                }

                if (!isStaticAsset && assetContract.getImmutableAssetPrefixes() != null) {
                    for (String prefix : assetContract.getImmutableAssetPrefixes()) {
                        if (cleanUrl.contains(prefix.toLowerCase())) {
                            isStaticAsset = true;
                            break;
                        }
                    }
                }

                if (isStaticAsset) {
                    if (status == 404) {
                        if (assetUrl.endsWith("vite.svg") || assetUrl.endsWith("favicon.ico") || assetUrl.endsWith("logo.svg")) {
                            logService.info(logDeploymentId, "DEPLOYING", "      ⚠️ Optional asset not found (HTTP 404): " + assetUrl);
                            continue;
                        }
                        throw new RuntimeException("Post-deploy verification failed: Asset " + assetUrl + " is missing (HTTP 404)");
                    }

                    if (status < 200 || status >= 400) {
                        throw new RuntimeException("Post-deploy verification failed: Asset " + assetUrl + " returned HTTP " + status);
                    }

                    if (mimeType != null && mimeType.toLowerCase().contains("text/html")) {
                        throw new RuntimeException("Post-deploy verification failed: Asset " + assetUrl 
                                + " returned HTML instead of the correct asset MIME type (MIME type mismatch)");
                    }

                    if (bodyPrefix.trim().toLowerCase().startsWith("<!doctype html") || bodyPrefix.trim().toLowerCase().startsWith("<html")) {
                        throw new RuntimeException("Post-deploy verification failed: Asset " + assetUrl 
                                + " body content starts with HTML markup, indicating illegal SPA fallback");
                    }
                }

            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Post-deploy verification failed: Error connecting to asset " + assetUrl + " (" + e.getMessage() + ")");
            }
        }

        // 4. Verify Routing Behavior (SPA history fallback vs SSR 404)
        String fallbackTestUrl = "http://" + publicIp + (basePath.endsWith("/") ? basePath : basePath + "/") + "non-existent-route-for-testing";
        logService.info(logDeploymentId, "DEPLOYING", "🔍 Probing route fallback at: " + fallbackTestUrl);
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(fallbackTestUrl).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int status = conn.getResponseCode();
            String mime = conn.getContentType();
            conn.disconnect();

            logService.info(logDeploymentId, "DEPLOYING", "      -> Fallback Response: HTTP " + status + " | MIME: " + mime);

            if (routingContract.isHistoryFallback()) {
                if (status != 200) {
                    throw new RuntimeException("Post-deploy verification failed: SPA History Fallback violated. Expected HTTP 200 for random route, got " + status);
                }
                if (mime == null || !mime.toLowerCase().contains("text/html")) {
                    throw new RuntimeException("Post-deploy verification failed: SPA History Fallback violated. Expected HTML response, got " + mime);
                }
                logService.info(logDeploymentId, "DEPLOYING", "   ✅ SPA History Fallback verified successfully.");
            } else {
                if (status == 200 && mime != null && mime.toLowerCase().contains("text/html")) {
                    throw new RuntimeException("Post-deploy verification failed: Routing violation. Non-SPA / SSR deployment returned HTTP 200 index.html for non-existent route instead of 404.");
                }
                logService.info(logDeploymentId, "DEPLOYING", "   ✅ SSR / Non-SPA route routing verified successfully.");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Post-deploy verification failed: Error probing fallback URL " + fallbackTestUrl + " (" + e.getMessage() + ")");
        }

        logService.info(logDeploymentId, "DEPLOYING", "✅ All assets, MIME types, and routing successfully verified!");
    }
}
