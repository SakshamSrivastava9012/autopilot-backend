package com.autopilot.service.deployment.runtime.dependency;

import com.autopilot.dto.AwsCredentialsDto;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Consumer;

@Service
public class ServiceDependencyEngine {

    private final DependencyResolver resolver;
    private final DependencyNegotiationEngine negotiationEngine;
    private final DependencyProvisioner provisioner;
    private final DependencyValidator validator;
    private final DependencyInjector injector;
    private final DependencyFallbackEngine fallbackEngine;

    public ServiceDependencyEngine(
            DependencyResolver resolver,
            DependencyNegotiationEngine negotiationEngine,
            DependencyProvisioner provisioner,
            DependencyValidator validator,
            DependencyInjector injector,
            DependencyFallbackEngine fallbackEngine) {
        this.resolver = resolver;
        this.negotiationEngine = negotiationEngine;
        this.provisioner = provisioner;
        this.validator = validator;
        this.injector = injector;
        this.fallbackEngine = fallbackEngine;
    }

    public RuntimeDependencyContract orchestrate(
            List<String> dbTypes,
            List<String> cacheTypes,
            Map<String, String> rawEnv,
            String deploymentMode,
            String deploymentId,
            AwsCredentialsDto creds,
            String region,
            String ec2InstanceId,
            Consumer<String> progressLog
    ) {
        System.out.println("🌐 Orchestrating Service Dependencies V4.4...");
        
        List<DependencyDescriptor> descriptors = new ArrayList<>();
        List<String> preDeployCommands = new ArrayList<>();
        Map<String, String> negotiatedEnv = new LinkedHashMap<>(rawEnv);
        
        List<String> allTypes = new ArrayList<>();
        if (dbTypes != null) allTypes.addAll(dbTypes);
        if (cacheTypes != null) allTypes.addAll(cacheTypes);
        
        String defaultPreference = "MANAGED".equalsIgnoreCase(deploymentMode) ? "Provision Platform Managed" : "Run Docker Container";

        for (String type : allTypes) {
            progressLog.accept("🔍 Resolving dependency: " + type);
            DependencyDescriptor descriptor = resolver.resolveDependency(type, type + "-primary", rawEnv, defaultPreference);
            
            progressLog.accept("🤝 Negotiating provider for: " + type);
            DependencyReports.NegotiationReport negotiation = negotiationEngine.negotiate(descriptor, defaultPreference);
            String negotiatedProvider = negotiation.getNegotiatedProvider();
            
            progressLog.accept("🚀 Provisioning/Resolving contract for: " + type + " (Negotiated: " + negotiatedProvider + ")");
            DependencyProvisioner.ProvisionResult provisionResult = provisioner.provision(
                    descriptor, negotiatedProvider, deploymentId, creds, region, progressLog);
            
            CredentialContract contract = provisionResult.getContract();
            preDeployCommands.addAll(provisionResult.getPreDeployCommands());
            
            // ADR-009 / ADR-010 Compliance:
            // Pre-flight credential validation is STRICTLY reserved for EXISTING_EXTERNAL providers.
            // For DOCKER_RUNTIME and PLATFORM_MANAGED, validation MUST be deferred until runtime provisioning is complete.
            boolean isExternal = isExistingExternalProvider(negotiatedProvider);

            if (isExternal) {
                progressLog.accept("✅ Performing pre-flight credential validation for external dependency: " + type);
                DependencyReports.CredentialValidationReport validation = validator.validate(
                        contract, ec2InstanceId, region, creds);
                
                if (!validation.isSuccess()) {
                    progressLog.accept("❌ Pre-flight credential validation failed: " + validation.getFailureType());
                    throw new CredentialValidationException(validation);
                }
                progressLog.accept("✅ Pre-flight verification passed for external dependency: " + type);
            } else {
                progressLog.accept("ℹ️ ADR-009/010: Pre-flight credential validation deferred for provider [" 
                        + negotiatedProvider + "] (Post-provision validation will occur after container startup)");
            }
            
            // Framework Injection & Sanitization
            // Detect configuration style based on environment keys
            String style = "GENERIC_ENV";
            if (rawEnv.containsKey("SPRING_BOOT") || rawEnv.containsKey("SPRING_DATASOURCE_URL") || rawEnv.containsKey("SPRING_DATASOURCE_USERNAME")) {
                style = "SPRING_DATASOURCE";
            } else if (rawEnv.containsKey("PRISMA") || rawEnv.containsKey("DATABASE_URL")) {
                style = "DATABASE_URL_ONLY";
            }
            
            negotiatedEnv = injector.generateAndSanitizePayload(contract, style, negotiatedEnv);
            
            descriptor.setConnectionUri(contract.getUri());
            descriptors.add(descriptor);
        }
        
        return RuntimeDependencyContract.builder()
                .dependencyState("READY")
                .dependencies(descriptors)
                .startupTimeoutMs(30000)
                .negotiatedEnvVars(negotiatedEnv)
                .preDeployCommands(preDeployCommands)
                .build();
    }

    private boolean isExistingExternalProvider(String provider) {
        if (provider == null) return false;
        String p = provider.toUpperCase();
        return p.contains("EXISTING_EXTERNAL") || p.contains("EXTERNAL") || p.contains("ATLAS") || p.contains("NEON");
    }

    // Keep legacy fallback for compatibility
    public RuntimeDependencyContract orchestrateDependencies(List<String> detectedTypes) {
        System.out.println("🌐 Orchestrating Service Dependencies (legacy)...");
        List<DependencyDescriptor> descriptors = new ArrayList<>();
        for (String type : detectedTypes) {
            DependencyDescriptor descriptor = resolver.resolveDependency(type, type + "-primary", new HashMap<>(), "Run Docker Container");
            descriptors.add(descriptor);
        }
        return RuntimeDependencyContract.builder()
                .dependencyState("READY")
                .dependencies(descriptors)
                .startupTimeoutMs(30000)
                .build();
    }
}
