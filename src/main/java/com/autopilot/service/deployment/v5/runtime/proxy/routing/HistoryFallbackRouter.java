package com.autopilot.service.deployment.v5.runtime.proxy.routing;

import org.springframework.stereotype.Service;

/**
 * Configures SPA HTML5 history fallback only when SPA capability exists.
 *
 * @since V5.4 — ADR-013
 */
@Service
public class HistoryFallbackRouter {

    public boolean isHistoryFallbackEnabled(String framework) {
        if (framework == null) return false;
        String fw = framework.toLowerCase();
        return fw.contains("react") || fw.contains("vue") || fw.contains("angular") || fw.contains("vite") || fw.contains("spa");
    }
}
