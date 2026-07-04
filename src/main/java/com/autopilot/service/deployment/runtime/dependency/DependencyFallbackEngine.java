package com.autopilot.service.deployment.runtime.dependency;

import org.springframework.stereotype.Service;

@Service
public class DependencyFallbackEngine {

    public DependencyReports.FallbackReport attemptFallback(DependencyDescriptor descriptor, String failedProvider) {
        System.out.println("⚠️ Dependency Failed: " + descriptor.getName() + " on " + failedProvider);
        System.out.println("  -> Attempting Fallback to DOCKER_RUNTIME...");
        
        return DependencyReports.FallbackReport.builder()
                .dependencyName(descriptor.getName())
                .fallbackTriggered(true)
                .originalProvider(failedProvider)
                .fallbackProvider("DOCKER_RUNTIME")
                .fallbackReason("Original provider unreachable during pre-flight validation.")
                .build();
    }
}
