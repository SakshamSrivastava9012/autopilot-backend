package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.PackageManager;
import com.autopilot.analyzer.model.RuntimeType;
import com.autopilot.service.deployment.strategies.RuntimeStrategies;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaticSiteRuntimeStrategyTest {

    @Test
    public void testStaticSiteRuntimeStrategyNginxConfigWithPrefix() {
        RuntimeStrategies.StaticSiteRuntimeStrategy strategy = new RuntimeStrategies.StaticSiteRuntimeStrategy();
        
        FrameworkMetadata metadataWithPrefix = FrameworkMetadata.builder()
                .name("test-frontend")
                .runtimeType(RuntimeType.STATIC)
                .packageManager(PackageManager.NPM)
                .buildCommand("npm run build")
                .outputDirectory("dist")
                .basePath("/app-12345")
                .build();
                
        String dockerfile = strategy.generateDockerfile(metadataWithPrefix);
        
        // Assert that rewrite rule is generated and injected into the dynamic nginx config
        assertTrue(dockerfile.contains("rewrite ^/app-12345(/?.*)$ $1 last;"));
    }

    @Test
    public void testStaticSiteRuntimeStrategyNginxConfigWithoutPrefix() {
        RuntimeStrategies.StaticSiteRuntimeStrategy strategy = new RuntimeStrategies.StaticSiteRuntimeStrategy();
        
        FrameworkMetadata metadataNoPrefix = FrameworkMetadata.builder()
                .name("test-frontend")
                .runtimeType(RuntimeType.STATIC)
                .packageManager(PackageManager.NPM)
                .buildCommand("npm run build")
                .outputDirectory("dist")
                .basePath("/")
                .build();
                
        String dockerfile = strategy.generateDockerfile(metadataNoPrefix);
        
        // Assert that rewrite rule is NOT generated when basePath is root
        assertFalse(dockerfile.contains("rewrite ^"));
    }
}
