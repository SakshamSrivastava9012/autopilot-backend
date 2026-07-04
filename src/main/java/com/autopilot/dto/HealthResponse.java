package com.autopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HealthResponse {
    private String status;
    private String version;
    private String gitCommit;
    private String buildTimestamp;
    private String jvmPid;
    private String applicationStartTime;
    private String runningJarPath;
    private String runningJarChecksum;
}
