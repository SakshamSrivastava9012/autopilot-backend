package com.autopilot.analyzer;

import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.dto.ServiceRole;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class ServiceClassifier {

    private static final Set<String> FRONTEND_FRAMEWORKS = Set.of(
            "react", "next", "nextjs", "vue", "nuxt", "nuxtjs",
            "angular", "svelte", "sveltekit", "gatsby", "astro",
            "vite", "remix", "solid", "preact", "html", "static"
    );

    private static final Set<String> BACKEND_FRAMEWORKS = Set.of(
            "spring", "springboot", "spring-boot", "express", "nestjs", "nest",
            "django", "flask", "fastapi", "gin", "fiber", "echo",
            "rails", "laravel", "actix", "rocket", "koa", "hapi",
            "quarkus", "micronaut", "ktor"
    );

    public ServiceRole classifyServiceRole(ServiceConfig s) {
        if (s.getFramework() == null) {
            return ServiceRole.UNKNOWN;
        }
        String fw = s.getFramework().toLowerCase().replaceAll("[\\s._-]+", "");
        String id = s.getServiceId().toLowerCase();

        if (id.contains("graphql")) {
            return ServiceRole.GRAPHQL;
        }
        if (id.contains("websocket") || id.contains("socket")) {
            return ServiceRole.WEBSOCKET;
        }
        if (id.contains("cron")) {
            return ServiceRole.CRON;
        }
        if (id.contains("worker") || id.contains("job")) {
            return ServiceRole.WORKER;
        }
        if (id.contains("redis") || id.contains("memcached")) {
            return ServiceRole.CACHE;
        }
        if (id.contains("mysql") || id.contains("postgres") || id.contains("mongo") || id.contains("db")) {
            return ServiceRole.DATABASE;
        }
        if (id.contains("kafka") || id.contains("rabbitmq")) {
            return ServiceRole.MESSAGE_BROKER;
        }
        if (id.contains("s3") || id.contains("minio")) {
            return ServiceRole.OBJECT_STORAGE;
        }
        if (id.contains("proxy") || id.contains("nginx")) {
            return ServiceRole.PROXY;
        }

        if (fw.contains("next") || fw.contains("nuxt") || fw.contains("sveltekit") || fw.contains("remix") || fw.contains("ssr")) {
            return ServiceRole.SSR;
        }

        if (fw.contains("react") || fw.contains("vue") || fw.contains("angular") || fw.contains("preact") || fw.contains("spa")) {
            return ServiceRole.SPA;
        }

        if (fw.contains("html") || fw.contains("static")) {
            return ServiceRole.STATIC_SITE;
        }

        if (BACKEND_FRAMEWORKS.stream().anyMatch(fw::contains) || id.contains("api") || id.contains("backend") || id.contains("server")) {
            return ServiceRole.API;
        }

        return ServiceRole.UNKNOWN;
    }

    public String classifyRole(ServiceConfig s) {
        if (s.getFramework() == null) {
            return "worker";
        }
        String fw = s.getFramework().toLowerCase().replaceAll("[\\s._-]+", "");
        
        if (FRONTEND_FRAMEWORKS.stream().anyMatch(fw::contains)) {
            return "frontend";
        }

        String name = s.getServiceId().toLowerCase();
        if (name.contains("worker") || name.contains("job") || name.contains("cron")) {
            return "worker";
        }

        if (BACKEND_FRAMEWORKS.stream().anyMatch(fw::contains)) {
            return "api";
        }

        return "service";
    }
}
