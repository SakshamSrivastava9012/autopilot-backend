package com.autopilot.service.deployment.v5.runtime.proxy.adapter;

import com.autopilot.service.deployment.v5.runtime.proxy.model.ReverseProxyModel;

/**
 * Proxy-agnostic adapter interface for generating and reloading proxy servers.
 *
 * @since V5.4 — ADR-013
 */
public interface ReverseProxyAdapter {

    boolean supports(String proxyType);

    String generateConfig(ReverseProxyModel model);

    boolean reload(String proxyType);

    boolean verifyConfig(String config);
}
