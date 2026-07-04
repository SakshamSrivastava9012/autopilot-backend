package com.autopilot.analyzer.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeContract {
    private RoutingContract routing;
    private AssetContract assets;
    private HealthContract health;
    private OAuthContract oauth;
    private RuntimeCapabilities capabilities;
    private String externalBrowserUrl;
}
