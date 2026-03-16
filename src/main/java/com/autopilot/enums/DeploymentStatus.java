package com.autopilot.enums;

public enum DeploymentStatus {

    PENDING,

    CLONING,

    ANALYZING,

    BUILDING_IMAGE,

    IMAGE_BUILT,

    PUSHING_IMAGE,

    IMAGE_PUSHED,

    PROVISIONING_INFRA,

    INFRA_CREATED,

    DEPLOYING,

    RUNNING,

    SUCCESS,

    FAILED
}
