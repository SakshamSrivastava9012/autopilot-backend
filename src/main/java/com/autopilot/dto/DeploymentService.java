package com.autopilot.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.autopilot.analyzer.runtime.RuntimeContract;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentService {
    String deploymentId;
    String serviceId;
    String name;
    String framework;
    String language;
    String serviceRoot;
    String dockerContext;
    String dockerfileLocation;
    String buildCommand;
    String startCommand;
    int port; // container port
    int hostPort;
    String basePath;
    String imageUri;
    String role;
    String runtimeVersion;
    String containerName;

    String healthPath;
    String protocol;
    List<Integer> expectedStatusCodes;
    int startupTimeout;
    int retryPolicy;

    RuntimeContract runtimeContract;
}
