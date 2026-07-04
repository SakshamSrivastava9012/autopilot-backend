package com.autopilot.analyzer.runtime;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class FrontendRuntimeStrategyRegistry {
    private final Map<String, FrontendRuntimeStrategy> strategies = new HashMap<>();

    public FrontendRuntimeStrategyRegistry() {
        strategies.put("react", new ReactSpaStrategy());
        strategies.put("vite", new ReactSpaStrategy());
        strategies.put("next", new NextSsrStrategy());
        strategies.put("nextjs", new NextSsrStrategy());
        strategies.put("vue", new VueSpaStrategy());
        strategies.put("angular", new AngularSpaStrategy());
        strategies.put("nuxt", new NuxtHybridStrategy());
        strategies.put("astro", new AstroStaticStrategy());
        strategies.put("static", new StaticHtmlStrategy());
        strategies.put("html", new StaticHtmlStrategy());
    }

    public Optional<FrontendRuntimeStrategy> getStrategy(String framework) {
        if (framework == null) return Optional.empty();
        String key = framework.toLowerCase().trim();
        if (key.startsWith("strategy_")) {
            key = key.substring("strategy_".length());
        }
        return Optional.ofNullable(strategies.get(key));
    }
}
