package com.autopilot.dto;

import lombok.Data;

@Data
public class TerraformResult {
    private String instanceId;
    private String publicIp;
}