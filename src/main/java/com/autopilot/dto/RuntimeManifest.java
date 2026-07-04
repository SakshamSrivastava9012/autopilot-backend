package com.autopilot.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeManifest {
    private String deploymentId;
    private String basePath;
    private String apiBasePath;
    private String assetPrefix;
    private String origin;
    private List<RouteDescriptor> routes;
    private List<ServiceDescriptor> services;
    private List<AssetManifestEntry> assets;
    private List<String> capabilities;
}
