package com.autopilot.service.deployment.v5.runtime.startup.engine;

import com.autopilot.service.deployment.v5.runtime.environment.injector.ContainerEnvironment;
import com.autopilot.service.deployment.v5.runtime.environment.resolver.RuntimeConnectionContract;
import com.autopilot.service.deployment.v5.runtime.startup.negotiation.StartupContract;
import com.autopilot.service.deployment.v5.runtime.startup.negotiation.StartupProfile;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Framework-Aware Startup Negotiation Engine.
 *
 * Negotiates startup strategy, readiness probes, and health endpoints based on detected
 * runtime capability — NOT framework log markers.
 *
 * Startup verification rules per framework:
 *   Spring Boot    → Container running → JVM running → Port bound → HTTP probe → Actuator if available → "Started" log marker OPTIONAL
 *   React + Nginx  → Container running → nginx master process → Port 80 → GET / → GET index.html → READY
 *   Static HTML    → HTTP probe only
 *   Express        → Port → HTTP → Process alive
 *   FastAPI        → HTTP → Process → Port
 *   Django         → HTTP → Process → Port
 *   Next.js SSR    → HTTP → Process → Port
 *   Angular        → HTTP → Port
 *   Nuxt           → HTTP → Process
 *   Laravel        → PHP-FPM → Nginx → HTTP
 *
 * No log marker is ever required for readiness determination.
 *
 * @since V5.3 — ADR-011 / Milestone 5.3
 */
@Service
public class StartupNegotiationEngineV5 {

    private static final List<Integer> DEFAULT_HEALTHY_STATUSES = Collections.unmodifiableList(Arrays.asList(
            200, 201, 202, 204, 301, 302, 303, 307, 308, 401, 403
    ));

    public StartupContract negotiateStartupContract(String serviceId, String framework,
                                                    ContainerEnvironment environment,
                                                    List<RuntimeConnectionContract> connections) {
        String contractId = "startup-contract-" + UUID.randomUUID().toString().substring(0, 8);
        String fw = framework != null ? framework.toLowerCase() : "generic";
        System.out.println("🤝 Startup Negotiation Engine V5 — Negotiating startup contract ["
                + contractId + "] for service [" + serviceId + "] (framework: " + fw + ")");

        StartupProfile profile = resolveStartupProfile(fw);

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("framework", fw);
        meta.put("negotiatedBy", "StartupNegotiationEngineV5");
        meta.put("startupStrategy", profile.getStrategyName());
        meta.put("logMarkerRequired", "false");
        meta.put("readinessMode", profile.getSuccessCriteria());
        meta.put("startupProfile", profile.name());

        return StartupContract.builder()
                .contractId(contractId)
                .serviceId(serviceId != null ? serviceId : "main-app")
                .startupStrategy(profile.getStrategyName())
                .readinessEndpoint(profile.getReadinessEndpoints().get(0))
                .healthEndpoint(profile.getHealthEndpoints().get(0))
                .expectedPort(profile.getDefaultPort())
                .expectedStatusCodes(DEFAULT_HEALTHY_STATUSES)
                .readinessTimeoutMs(profile.getDefaultReadinessTimeoutMs())
                .healthTimeoutMs(profile.getDefaultHealthTimeoutMs())
                .maxAdaptiveExtensionMs(profile.getDefaultReadinessTimeoutMs() * 2)
                .metadata(meta)
                .build();
    }

    private StartupProfile resolveStartupProfile(String fw) {
        if (fw.contains("spring") || fw.contains("quarkus") || fw.contains("micronaut")) {
            return StartupProfile.SPRING_BOOT;
        }
        if (fw.contains("react") || fw.contains("vite") || fw.contains("angular") || fw.contains("vue")) {
            return StartupProfile.NGINX_STATIC;
        }
        if (fw.contains("static") || fw.contains("html")) {
            return StartupProfile.STATIC_SITE;
        }
        if (fw.contains("express") || fw.contains("nest") || fw.contains("node") || fw.contains("next") || fw.contains("nuxt")) {
            return StartupProfile.NODE_SERVER;
        }
        if (fw.contains("fastapi") || fw.contains("django") || fw.contains("flask") || fw.contains("python")) {
            return StartupProfile.PYTHON;
        }
        if (fw.contains("go") || fw.contains("golang")) {
            return StartupProfile.GO;
        }
        if (fw.contains("rust")) {
            return StartupProfile.RUST;
        }
        if (fw.contains("java") || fw.contains("kotlin") || fw.contains("jvm")) {
            return StartupProfile.JVM;
        }
        return StartupProfile.NODE_SERVER; // fallback default
    }
}
