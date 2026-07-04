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
public class DeploymentManifest {
    String deploymentId;
    String application;
    List<ServiceDescriptor> services;
    List<RouteDescriptor> routes;
    List<String> volumes;
    List<String> networks;
    List<AssetManifestEntry> assets;
    List<String> capabilities;
    EnvironmentContract environmentContract;
    DatabaseDescriptor database;
    java.util.Map<String, Object> runtimeReports;
}
