package com.autopilot.analyzer.runtime;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class CapabilityRegistry {
    private final FrontendRuntimeStrategyRegistry strategyRegistry;

    public CapabilityRegistry(FrontendRuntimeStrategyRegistry strategyRegistry) {
        this.strategyRegistry = strategyRegistry;
    }

    public RuntimeCapabilities getCapabilities(String framework) {
        Optional<FrontendRuntimeStrategy> strat = strategyRegistry.getStrategy(framework);
        if (strat.isPresent()) {
            return strat.get().capabilities();
        }
        Set<CapabilityType> types = new HashSet<>();
        if (framework != null) {
            String fw = framework.toLowerCase();
            if (fw.contains("spring") || fw.contains("express") || fw.contains("nest") || fw.contains("django") || fw.contains("flask") || fw.contains("laravel") || fw.contains("fastapi")) {
                types.add(CapabilityType.NODE_SERVER);
            } else {
                types.add(CapabilityType.STATIC_ASSETS);
            }
        }
        return RuntimeCapabilities.builder().types(types).build();
    }
}
