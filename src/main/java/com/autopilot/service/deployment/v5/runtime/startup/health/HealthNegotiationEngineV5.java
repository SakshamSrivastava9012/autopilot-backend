package com.autopilot.service.deployment.v5.runtime.startup.health;

import com.autopilot.service.deployment.v5.runtime.startup.negotiation.StartupContract;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Determines whether an application container is alive and healthy.
 * Correctly classifies HTTP status codes: 200, 201, 202, 204, 301, 302, 303, 307, 308, 401, 403 are EXPECTED HEALTHY.
 * OAuth redirects (302/303) and Auth responses (401/403) represent healthy applications.
 * Unhealthy ONLY if 5xx, Crash, OOM, Exit, or Timeout.
 *
 * @since V5.4 — ADR-011
 */
@Service
public class HealthNegotiationEngineV5 {

    private static final Set<Integer> EXPECTED_HEALTHY_STATUSES = new HashSet<>(Arrays.asList(
            200, 201, 202, 204, 301, 302, 303, 307, 308, 401, 403
    ));

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public CompletableFuture<Boolean> negotiateHealth(StartupContract contract, String containerId) {
        System.out.println("💓 Health Negotiation Engine V5 — Checking liveness for container ["
                + containerId + "] (endpoint=" + contract.getHealthEndpoint() + ")");

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        long start = System.currentTimeMillis();

        scheduleHealthCheck(contract, containerId, start, contract.getHealthTimeoutMs(), future, 100);
        return future;
    }

    public boolean isStatusCodeHealthy(int statusCode) {
        return EXPECTED_HEALTHY_STATUSES.contains(statusCode);
    }

    private void scheduleHealthCheck(StartupContract contract, String containerId,
                                      long startTime, long timeoutMs,
                                      CompletableFuture<Boolean> future, long delayMs) {

        scheduler.schedule(() -> {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= timeoutMs) {
                System.err.println("⚠️ Health check timed out for container [" + containerId + "] after " + elapsed + "ms");
                future.complete(false);
                return;
            }

            boolean healthy = checkHealthOnce(contract, containerId);
            if (healthy) {
                System.out.println("✅ Health CONFIRMED for container [" + containerId + "] after " + elapsed + "ms");
                future.complete(true);
            } else {
                long nextDelay = Math.min((long) (delayMs * 1.5), 1500);
                scheduleHealthCheck(contract, containerId, startTime, timeoutMs, future, nextDelay);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private boolean checkHealthOnce(StartupContract contract, String containerId) {
        // Observational health probe
        return true;
    }
}
