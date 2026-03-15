package com.autopilot.dto;

import lombok.Data;

@Data
public class DeployRequest {

    private String repoUrl;
    private String branch;
    private String projectName;

    private String buildCommand;
    private String startCommand;

    private Integer port;
    private String environment;

    // traffic estimate
    private Integer expectedUsers;   // e.g. 100, 1000, 10000

    // AWS config
    private String awsRoleArn;
    private String awsRegion;
}