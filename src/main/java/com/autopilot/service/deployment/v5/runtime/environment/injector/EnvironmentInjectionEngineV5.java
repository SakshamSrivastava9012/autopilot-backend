package com.autopilot.service.deployment.v5.runtime.environment.injector;

import com.autopilot.service.deployment.v5.runtime.environment.mapper.FrameworkConfigurationMapper;
import com.autopilot.service.deployment.v5.runtime.environment.report.EnvironmentReports;
import com.autopilot.service.deployment.v5.runtime.environment.resolver.RuntimeConnectionContract;
import com.autopilot.service.deployment.v5.runtime.environment.sanitizer.ConfigurationSanitizer;
import com.autopilot.service.deployment.v5.runtime.environment.secret.SecretReferenceResolver;
import com.autopilot.service.deployment.v5.runtime.environment.snapshot.RuntimeEnvironmentSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * The Universal Runtime Connection Resolution & Environment Injection Engine.
 *
 * Converts immutable RuntimeConnectionContracts into framework-specific ContainerEnvironments.
 * It is the ONLY component allowed to create environment variables for application containers.
 *
 * It NEVER modifies repository files, Dockerfiles, source code, application.yml, or .env files.
 *
 * Feature flag gated by deployrix.runtime.environment=v5
 *
 * @since V5.4 — ADR-010
 */
@Service
public class EnvironmentInjectionEngineV5 {

    private final FrameworkConfigurationMapper frameworkMapper;
    private final ConfigurationSanitizer sanitizer;
    private final SecretReferenceResolver secretResolver;

    @Value("${deployrix.runtime.environment:v5}")
    private String environmentEngineMode;

    public EnvironmentInjectionEngineV5(FrameworkConfigurationMapper frameworkMapper,
                                         ConfigurationSanitizer sanitizer,
                                         SecretReferenceResolver secretResolver) {
        this.frameworkMapper = frameworkMapper;
        this.sanitizer = sanitizer;
        this.secretResolver = secretResolver;
    }

    public boolean isV5Enabled() {
        return "v5".equalsIgnoreCase(environmentEngineMode);
    }

    public InjectionResult generateEnvironment(List<RuntimeConnectionContract> connections,
                                                String framework,
                                                Map<String, String> userCustomVars) {
        String envId = "env-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("⚡ Environment Injection Engine V5 — Generating environment [" + envId
                + "] for framework: [" + framework + "] with " + connections.size() + " connection contracts...");
        long start = System.currentTimeMillis();

        // 1. Framework Configuration Mapping (Deterministic 1-to-1)
        Map<String, String> rawMapped = new LinkedHashMap<>(frameworkMapper.mapToFramework(connections, framework));
        if (userCustomVars != null) {
            rawMapped.putAll(userCustomVars);
        }

        // 2. Configuration Sanitization (remove conflicts & repo dev values)
        ConfigurationSanitizer.SanitizationResult sanitizationResult = sanitizer.sanitize(rawMapped, framework);

        // 3. Secret Reference Resolution (provider-agnostic)
        SecretReferenceResolver.SecretResolutionResult secretResult = secretResolver.resolveSecrets(sanitizationResult.getSanitizedEnvironment());

        long duration = System.currentTimeMillis() - start;

        // 4. Build ContainerEnvironment Output
        ContainerEnvironment containerEnvironment = ContainerEnvironment.builder()
                .environmentId(envId)
                .variables(secretResult.getResolvedEnvironment())
                .maskedVariables(secretResult.getMaskedEnvironment())
                .secretReferences(secretResult.getResolvedSecretReferences())
                .framework(framework != null ? framework : "generic")
                .generatedAtEpoch(start)
                .metadata(Collections.singletonMap("injectionEngine", "V5"))
                .build();

        // 5. Build Snapshot & Reports
        RuntimeEnvironmentSnapshot snapshot = RuntimeEnvironmentSnapshot.builder()
                .deploymentId(envId)
                .generatedVariables(secretResult.getMaskedEnvironment())
                .removedVariables(sanitizationResult.getRemovedVariables())
                .injectedSecrets(secretResult.getResolvedSecretReferences())
                .frameworkMapping(framework != null ? framework : "generic")
                .warnings(Collections.emptyList())
                .metadata(Collections.emptyMap())
                .build();

        EnvironmentReports.EnvironmentInjectionReport injectionReport = EnvironmentReports.EnvironmentInjectionReport.builder()
                .environmentId(envId)
                .framework(framework)
                .success(true)
                .durationMs(duration)
                .totalVariablesInjected(secretResult.getResolvedEnvironment().size())
                .secretsResolved(secretResult.getResolvedSecretReferences().size())
                .logs(Collections.singletonList("Environment generated cleanly for " + framework))
                .warnings(Collections.emptyList())
                .build();

        EnvironmentReports.FrameworkMappingReport mappingReport = EnvironmentReports.FrameworkMappingReport.builder()
                .framework(framework)
                .connectionCount(connections.size())
                .mappedKeys(secretResult.getMaskedEnvironment())
                .build();

        EnvironmentReports.ConfigurationSanitizationReport sanitizationReport = EnvironmentReports.ConfigurationSanitizationReport.builder()
                .variablesRemoved(sanitizationResult.getRemovedVariables().size())
                .removedReasonList(sanitizationResult.getRemovedVariables())
                .build();

        EnvironmentReports.SecretResolutionReport secretReport = EnvironmentReports.SecretResolutionReport.builder()
                .secretsResolvedCount(secretResult.getResolvedSecretReferences().size())
                .resolvedSecretKeys(secretResult.getResolvedSecretReferences())
                .build();

        return new InjectionResult(containerEnvironment, snapshot, injectionReport, mappingReport, sanitizationReport, secretReport);
    }

    @lombok.Value
    public static class InjectionResult {
        ContainerEnvironment containerEnvironment;
        RuntimeEnvironmentSnapshot snapshot;
        EnvironmentReports.EnvironmentInjectionReport injectionReport;
        EnvironmentReports.FrameworkMappingReport mappingReport;
        EnvironmentReports.ConfigurationSanitizationReport sanitizationReport;
        EnvironmentReports.SecretResolutionReport secretReport;
    }
}
