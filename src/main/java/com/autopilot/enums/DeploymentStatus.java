package com.autopilot.enums;

import jakarta.annotation.Resource;

public enum DeploymentStatus {

    PENDING,
    ANALYZING,
    BUILDING,
    IMAGE_BUILT,
    PROVISIONING,
    DEPLOYING,
    RUNNING,
    SUCCESS,
    FAILED,
    IMAGE_PUSHED;
}