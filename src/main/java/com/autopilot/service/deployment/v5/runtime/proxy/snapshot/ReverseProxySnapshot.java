package com.autopilot.service.deployment.v5.runtime.proxy.snapshot;

import com.autopilot.service.deployment.v5.runtime.proxy.model.ReverseProxyModel;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of generated reverse proxy configuration, routing tables, and reload status.
 *
 * @since V5.4 — ADR-013
 */
@Value
@Builder
public class ReverseProxySnapshot {
    String deploymentId;
    String proxyType;
    String generatedConfig;
    ReverseProxyModel proxyModel;
    boolean configVerified;
    boolean reloadSuccessful;
    List<String> activeRoutes;
    Map<String, String> metadata;
}
