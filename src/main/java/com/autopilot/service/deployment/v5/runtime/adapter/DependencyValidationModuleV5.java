package com.autopilot.service.deployment.v5.runtime.adapter;

import com.autopilot.service.deployment.v5.negotiation.DependencyContract;
import com.autopilot.service.deployment.v5.runtime.contract.ExecutionResult;
import com.autopilot.service.deployment.v5.runtime.contract.RollbackResult;
import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import com.autopilot.service.deployment.v5.runtime.dependency.contract.RuntimeDependency;
import com.autopilot.service.deployment.v5.runtime.dependency.credential.ResolvedCredentialContract;
import com.autopilot.service.deployment.v5.runtime.dependency.validation.DependencyValidationReport;
import com.autopilot.service.deployment.v5.runtime.dependency.validation.DependencyValidator;
import com.autopilot.service.deployment.v5.runtime.engine.RuntimeModule;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionNode;
import com.autopilot.service.deployment.v5.runtime.graph.ExecutionPhase;
import com.autopilot.service.deployment.v5.runtime.dependency.report.ValidationReport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Dependency validation module enforcing ADR-009 / ADR-010 provider-specific execution graph node order.
 *
 * For DOCKER_RUNTIME and PLATFORM_MANAGED: dependency-validation-node explicitly depends on dependency-node.
 * For EXISTING_EXTERNAL: dependency-validation-node depends on infrastructure-node (executed pre-flight).
 *
 * @since V5.5 — ADR-009 / ADR-010 Compliance
 */
@Component
public class DependencyValidationModuleV5 implements RuntimeModule {

    private final DependencyValidator validator;

    public DependencyValidationModuleV5(@Qualifier("v5DependencyValidator") DependencyValidator validator) {
        this.validator = validator;
    }

    @Override public String id() { return "dependency-validation-module"; }

    @Override public boolean supports(RuntimeContext context) { return true; }

    @Override
    public ExecutionNode createNode(RuntimeContext context) {
        String provider = resolveProviderFromContext(context);
        boolean isExternal = validator.isExistingExternal(provider);
        List<String> dependsOn = isExternal
                ? Collections.singletonList("infrastructure-node")
                : Collections.singletonList("dependency-node");

        return new AbstractRuntimeNode("dependency-validation-node", "Dependency Validation Engine", ExecutionPhase.DEPENDENCIES, dependsOn) {
            @Override
            @SuppressWarnings("unchecked")
            public ExecutionResult execute(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                System.out.println("🔍 Executing Graph Node: [dependency-validation-node] (Provider: " + provider + ")");

                Map<String, RuntimeDependency> runtimeDeps = (Map<String, RuntimeDependency>) ctx.getResolvedObject("RuntimeDependencies");
                Map<String, ResolvedCredentialContract> resolvedCreds = (Map<String, ResolvedCredentialContract>) ctx.getResolvedObject("ResolvedCredentialContracts");
                DependencyContract contract = (DependencyContract) ctx.getResolvedObject("DependencyContract");

                Map<String, DependencyValidationReport> validationReports = new LinkedHashMap<>();
                List<String> logs = new ArrayList<>();

                if (runtimeDeps != null && !runtimeDeps.isEmpty()) {
                    for (Map.Entry<String, RuntimeDependency> entry : runtimeDeps.entrySet()) {
                        String depId = entry.getKey();
                        RuntimeDependency rDep = entry.getValue();
                        ResolvedCredentialContract creds = resolvedCreds != null ? resolvedCreds.get(depId) : null;
                        DependencyValidationReport report = validator.validate(contract, rDep, creds);
                        validationReports.put(depId, report);
                        logs.addAll(report.getLogs());
                    }
                } else if (contract != null) {
                    ResolvedCredentialContract creds = resolvedCreds != null ? resolvedCreds.get(contract.getDependencyId()) : null;
                    DependencyValidationReport report = validator.validate(contract, null, creds);
                    validationReports.put(contract.getDependencyId(), report);
                    logs.addAll(report.getLogs());
                } else {
                    // Default fallback validation
                    DependencyContract defaultContract = DependencyContract.builder()
                            .dependencyId("primary-db")
                            .provider(provider)
                            .build();
                    DependencyValidationReport report = validator.validate(defaultContract, null, null);
                    validationReports.put("primary-db", report);
                    logs.addAll(report.getLogs());
                }

                ctx.putResolvedObject("DependencyValidationReports", validationReports);

                if (!validationReports.isEmpty()) {
                    var entry = validationReports.entrySet().iterator().next();
                    var depValReport = entry.getValue();
                    ValidationReport valReport = ValidationReport.builder()
                            .dependencyId(entry.getKey())
                            .provider(provider)
                            .validated(depValReport.isValidated())
                            .deferred(depValReport.isDeferred())
                            .validationPhase(depValReport.getValidationPhase())
                            .endpointValidated(depValReport.getEndpointValidated())
                            .logs(depValReport.getLogs())
                            .warnings(depValReport.getWarnings())
                            .build();
                    ctx.putResolvedObject("ValidationReport", valReport);
                }

                Map<String, Object> outputs = new HashMap<>();
                outputs.put("validatedCount", validationReports.size());
                outputs.put("provider", provider);

                return ExecutionResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Dependency validation phase completed for provider: " + provider)
                        .outputs(outputs)
                        .logs(logs)
                        .warnings(Collections.emptyList())
                        .executionDurationMs(System.currentTimeMillis() - start)
                        .build();
            }

            @Override
            public RollbackResult rollback(RuntimeContext ctx) {
                long start = System.currentTimeMillis();
                return RollbackResult.builder()
                        .success(true)
                        .nodeId(getId())
                        .message("Dependency validation node rollback clean")
                        .logs(Collections.singletonList("Validation state cleared"))
                        .warnings(Collections.emptyList())
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }
        };
    }

    private String resolveProviderFromContext(RuntimeContext context) {
        if (context != null) {
            Object providerObj = context.getResolvedObject("TargetProvider");
            if (providerObj instanceof String) return (String) providerObj;

            Object contractObj = context.getResolvedObject("DependencyContract");
            if (contractObj instanceof DependencyContract) {
                DependencyContract dc = (DependencyContract) contractObj;
                if (dc.getProvider() != null) return dc.getProvider();
            }
        }
        return "DOCKER_RUNTIME";
    }
}
