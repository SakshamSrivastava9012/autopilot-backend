package com.autopilot.controller;

import com.autopilot.dto.HealthResponse;
import com.autopilot.service.BuildMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final BuildMetadataService metadataService;

    @GetMapping("/health")
    public HealthResponse getHealth() {
        return new HealthResponse(
                "UP",
                metadataService.getVersion(),
                metadataService.getGitCommit(),
                metadataService.getBuildTimestamp(),
                metadataService.getPid(),
                new Date(metadataService.getStartTime()).toString(),
                metadataService.getRunningJarPath(),
                metadataService.getRunningJarChecksum()
        );
    }
}
