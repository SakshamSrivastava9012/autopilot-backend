package com.autopilot.dto;

import lombok.Data;
import java.util.Map;

@Data
public class DeployRequest {

    private String repoUrl;
    private String branch;
    private String projectName;

    // MANAGED (deploy on Autopilot's AWS) | BYOC (user provides their own AWS ARN)
    // Defaults to BYOC for backward compatibility
    private String deploymentMode;

    private String buildCommand;
    private String startCommand;

    private Integer port;
    private String environment;

    // traffic estimate
    private Integer expectedUsers;   // e.g. 100, 1000, 10000

    // AWS config (required for BYOC, ignored for MANAGED)
    private String awsRoleArn;
    private String awsRegion;

    // Custom environment variables
    private Map<String, String> envVars;

    private String instanceTypeOverride;
}