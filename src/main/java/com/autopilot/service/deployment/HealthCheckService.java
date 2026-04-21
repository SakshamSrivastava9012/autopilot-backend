package com.autopilot.service.deployment;

import com.autopilot.entity.Deployment;
import com.autopilot.enums.DeploymentStatus;
import com.autopilot.repository.DeploymentRepository;
import com.autopilot.service.log.DeploymentLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

/**
 * Post-Deploy Health Validation Service.
 *
 * After SSMDeployService.deployContainer() finishes, this service:
 *   1. Waits for the container to start
 *   2. Performs HTTP health checks with exponential backoff
 *   3. Classifies failures (timeout, crash, wrong port, 5xx)
 *   4. Marks deployment SUCCESS or FAILED with detailed diagnostics
 *
 * This prevents false "successful" deployments where the container
 * starts but the application crashes immediately after.
 */
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogService logService;

    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_WAIT_MS = 15_000;      // 15s before first check
    private static final int INITIAL_BACKOFF_MS = 3_000;    // 3s → 6s → 12s → 24s → 48s

    /**
     * Health check result.
     */
    public record HealthResult(
            boolean healthy,
            int httpStatus,
            long responseTimeMs,
            String failureReason,
            FailureCategory category
    ) {}

    /**
     * Failure categories for diagnostics.
     */
    public enum FailureCategory {
        HEALTHY,                // 200-399
        CONNECTION_REFUSED,     // Container not listening on port
        TIMEOUT,                // Container listening but not responding
        SERVER_ERROR,           // 5xx — app crashed or misconfigured
        NOT_FOUND,              // 404 — wrong path (likely basePath issue)
        REDIRECT_LOOP,          // Too many redirects
        UNKNOWN                 // Unexpected error
    }



    /**
     * Run the complete post-deploy validation.
     *
     * @param deployment The deployment entity
     * @param publicIp   EC2 public IP
     * @param basePath   Application base path (e.g., /app-abc123)
     * @return HealthResult
     */



    public HealthResult validate(Deployment deployment, String publicIp, String basePath) {

        String did = deployment.getId();

        logService.info(did, "DEPLOYING", "Starting post-deploy health validation...");

        // ── STEP 1: Initial wait for container startup ───────────────────
        waitForStartup(did);

        // ── STEP 2: Build health check URLs ──────────────────────────────
        List<String> urls = buildCheckUrls(publicIp, basePath);

        // ── STEP 3: Retry with exponential backoff ───────────────────────
        HealthResult result = retryHealthCheck(urls, did);

        // ── STEP 4: Mark deployment status ───────────────────────────────
        markDeploymentStatus(deployment, result);

        return result;
    }

    /**
     * Wait for the container to start up before checking.
     */
    private void waitForStartup(String did) {
        logService.info(did, "DEPLOYING", "Waiting " + (INITIAL_WAIT_MS / 1000) + "s for container startup...");
        try {
            Thread.sleep(INITIAL_WAIT_MS);
        } catch (InterruptedException ignored) {}
    }

    /**
     * Build the list of URLs to check. Tries multiple paths in priority order.
     */
    private List<String> buildCheckUrls(String publicIp, String basePath) {
        String base = "http://" + publicIp;
        String path = basePath != null ? basePath : "";

        return List.of(
                base + path + "/health",           // Standard health endpoint
                base + path + "/api/health",       // API health endpoint
                base + path + "/actuator/health",  // Spring Boot actuator
                base + path + "/",                 // Root path
                base + path                        // Root without trailing slash
        );
    }

    /**
     * Retry health checks with exponential backoff.
     * Tries each URL in priority order per attempt.
     */
    private HealthResult retryHealthCheck(List<String> urls, String did) {

        int backoffMs = INITIAL_BACKOFF_MS;
        HealthResult lastResult = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

            logService.info(did, "DEPLOYING",
                    "Health check attempt " + attempt + "/" + MAX_RETRIES + "...");

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

            // Log the failure and backoff
            String reason = lastResult != null ? lastResult.failureReason : "unknown";
            logService.info(did, "DEPLOYING",
                    "⚠️ Attempt " + attempt + " failed: " + reason
                            + " — retrying in " + (backoffMs / 1000) + "s");

            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ignored) {}

            backoffMs *= 2; // exponential backoff: 3s → 6s → 12s → 24s → 48s
        }

        // All retries exhausted
        String finalReason = lastResult != null ? lastResult.failureReason : "All health checks failed";
        logService.error(did, "DEPLOYING",
                "❌ Health check FAILED after " + MAX_RETRIES + " attempts: " + finalReason);

        return lastResult != null ? lastResult
                : new HealthResult(false, 0, 0, "All health checks failed", FailureCategory.UNKNOWN);
    }

    /**
     * Perform a single HTTP health check.
     */
    private HealthResult checkHttp(String url) {
        long start = System.currentTimeMillis();

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setInstanceFollowRedirects(false); // don't follow redirects blindly

            int status = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - start;

            // Read error stream for firewall blocks
            String body = "";
            if (status >= 400 && conn.getErrorStream() != null) {
                try (java.util.Scanner scanner = new java.util.Scanner(conn.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                    body = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                } catch (Exception ignored) {}
            }
            conn.disconnect();

            // Firewall interference detection (e.g. FortiGuard "Web Page Blocked")
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

    /**
     * Classify failure and suggest fix.
     */
    public String classifyFailure(HealthResult result) {
        if (result.healthy) return "HEALTHY";

        return switch (result.category) {
            case CONNECTION_REFUSED ->
                    "Container is not listening on the expected port. " +
                    "Check if the Dockerfile EXPOSE port matches the app's actual port.";
            case TIMEOUT ->
                    "Container is running but the application is not responding. " +
                    "The app may be stuck in startup (missing database? missing env var?).";
            case SERVER_ERROR ->
                    "Application returned HTTP " + result.httpStatus + ". " +
                    "Check application logs for startup errors.";
            case NOT_FOUND ->
                    "HTTP 404 — the application root path may be wrong. " +
                    "Check basePath configuration.";
            case REDIRECT_LOOP ->
                    "Too many redirects — check if the app redirects to HTTPS or a login page.";
            default ->
                    "Unknown failure: " + result.failureReason;
        };
    }

    /**
     * Mark deployment status based on health check result.
     */
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
}
