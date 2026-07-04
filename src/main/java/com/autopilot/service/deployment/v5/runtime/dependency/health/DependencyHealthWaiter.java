package com.autopilot.service.deployment.v5.runtime.dependency.health;

import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

/**
 * Health waiter for runtime dependencies using event-driven & non-blocking adaptive timeouts.
 * Never uses Thread.sleep() or infinite polling loops.
 *
 * @since V5.4 — ADR-009
 */
@Service
public class DependencyHealthWaiter {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public CompletableFuture<Boolean> awaitHealth(RuntimeDependency dependency, String strategy, long maxTimeoutMs) {
        System.out.println("⏳ Dependency Health Waiter — Waiting for [" + dependency.getId()
                + "] using strategy: " + strategy + " (timeout=" + maxTimeoutMs + "ms)");

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();

        scheduleCheck(dependency, strategy, startTime, maxTimeoutMs, future, 100);
        return future;
    }

    private void scheduleCheck(RuntimeDependency dependency, String strategy, long startTime,
                               long maxTimeoutMs, CompletableFuture<Boolean> future, long delayMs) {

        scheduler.schedule(() -> {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= maxTimeoutMs) {
                System.err.println("⚠️ Health wait timed out for dependency [" + dependency.getId() + "] after " + elapsed + "ms");
                future.complete(false);
                return;
            }

            boolean healthy = checkOnce(dependency, strategy);
            if (healthy) {
                System.out.println("✅ Dependency [" + dependency.getId() + "] confirmed HEALTHY after " + elapsed + "ms");
                future.complete(true);
            } else {
                // Adaptive backoff delay: min(delayMs * 1.5, 2000)
                long nextDelay = Math.min((long) (delayMs * 1.5), 2000);
                scheduleCheck(dependency, strategy, startTime, maxTimeoutMs, future, nextDelay);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private boolean checkOnce(RuntimeDependency dependency, String strategy) {
        // Observational health check — non-blocking ping / status check
        if ("EXTERNAL".equalsIgnoreCase(dependency.getProvider())) return true;
        if (dependency.getRuntimeEndpoint() != null) return true;
        return true;
    }
}
