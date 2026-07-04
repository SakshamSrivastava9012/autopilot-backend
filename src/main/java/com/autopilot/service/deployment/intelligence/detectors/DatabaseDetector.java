package com.autopilot.service.deployment.intelligence.detectors;

import com.autopilot.service.deployment.intelligence.RepositoryModel;
import com.autopilot.service.deployment.intelligence.RepositoryScanner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Collections;
import com.autopilot.service.deployment.intelligence.DetectorResult;

@Component
public class DatabaseDetector implements RepositoryScanner {
    @Override
    public List<DetectorResult> scan(String repositoryPath) {
        return Collections.emptyList();
    }
}
