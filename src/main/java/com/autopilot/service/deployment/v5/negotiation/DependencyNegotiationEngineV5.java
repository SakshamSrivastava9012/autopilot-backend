package com.autopilot.service.deployment.v5.negotiation;

import com.autopilot.service.deployment.intelligence.v5.model.RepositoryModelV5;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * The Universal Dependency Negotiation Engine.
 *
 * Converts observed repository dependencies into deployment intent (DependencyContracts).
 * This is the Deployrix equivalent of the Kubernetes Scheduler — it DECIDES, it never EXECUTES.
 *
 * **Negotiation decides intent; provisioning executes intent.**
 * **Negotiation must never perform network I/O, DNS resolution, authentication,
 *   Docker operations, or cloud provisioning.**
 *
 * @since V5.2 — ADR-005
 */
@Service
public class DependencyNegotiationEngineV5 {

    private final DependencyIntelligenceEngine intelligenceEngine;
    private final ConfigurationClassifier classifier;

    public DependencyNegotiationEngineV5(
            DependencyIntelligenceEngine intelligenceEngine,
            ConfigurationClassifier classifier) {
        this.intelligenceEngine = intelligenceEngine;
        this.classifier = classifier;
    }

    /**
     * Negotiate all dependencies from the RepositoryModelV5.
     * Produces immutable DependencyContracts and NegotiationReports.
     *
     * @param model The immutable repository model (V5 Milestone 1).
     * @param userPreferences Map of dependency type -> ProviderPreference from the Deployrix UI.
     */
    public NegotiationResult negotiate(RepositoryModelV5 model, Map<String, ProviderPreference> userPreferences) {
        System.out.println("🤝 Dependency Negotiation Engine V5 — Processing " + model.getDependencies().size() + " dependencies...");

        List<DependencyIntelligenceEngine.DependencyIntelligence> intelligence = intelligenceEngine.analyze(model);
        List<DependencyContract> contracts = new ArrayList<>();
        List<NegotiationReport> reports = new ArrayList<>();

        for (DependencyIntelligenceEngine.DependencyIntelligence dep : intelligence) {
            ProviderPreference userPref = userPreferences != null
                    ? userPreferences.getOrDefault(dep.getType(), ProviderPreference.AUTOMATIC)
                    : ProviderPreference.AUTOMATIC;

            NegotiationDecision decision = decideProvider(dep, userPref);

            DependencyContract contract = DependencyContract.builder()
                    .dependencyId(UUID.randomUUID().toString().substring(0, 8))
                    .type(dep.getType())
                    .provider(decision.provider)
                    .version(dep.getDetectedVersion() != null ? dep.getDetectedVersion() : "unknown")
                    .uri(dep.getConnectionHint())
                    .ownership(decision.ownership)
                    .provisioningMode(decision.preference)
                    .endpointClassification(dep.getEndpointClassification())
                    .tls(dep.isProductionEndpoint())
                    .healthStrategy(inferHealthStrategy(dep.getType()))
                    .migrationStrategy("NONE")
                    .runtimeHints(Collections.emptyList())
                    .metadata(Collections.emptyMap())
                    .build();

            NegotiationReport report = NegotiationReport.builder()
                    .dependencyType(dep.getType())
                    .decision(decision.preference.name())
                    .confidence(decision.confidence)
                    .reason(decision.reason)
                    .evidence(decision.evidence)
                    .rulesMatched(decision.rulesMatched)
                    .warnings(decision.warnings)
                    .build();

            contracts.add(contract);
            reports.add(report);

            System.out.println("   " + dep.getType() + " → " + decision.preference
                    + " (confidence=" + decision.confidence + "%, reason: " + decision.reason + ")");
        }

        return new NegotiationResult(
                Collections.unmodifiableList(contracts),
                Collections.unmodifiableList(reports));
    }

    // ─── Decision Tree ─────────────────────────────────────────

    private NegotiationDecision decideProvider(
            DependencyIntelligenceEngine.DependencyIntelligence dep,
            ProviderPreference userPref) {

        List<String> rules = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Priority 1: Explicit user preference (never violated)
        if (userPref != ProviderPreference.AUTOMATIC) {
            rules.add("RULE_1_USER_PREFERENCE");
            evidence.add("User explicitly selected: " + userPref);
            return new NegotiationDecision(
                    resolveProvider(dep.getType(), userPref),
                    userPref,
                    userPref == ProviderPreference.EXISTING_EXTERNAL ? OwnershipType.USER : OwnershipType.PLATFORM,
                    98, "User explicit preference: " + userPref,
                    evidence, rules, warnings);
        }

        // Priority 2: Repository has a production endpoint
        if (dep.isProductionEndpoint()) {
            rules.add("RULE_2_PRODUCTION_ENDPOINT");
            evidence.add("Detected production endpoint: " + dep.getConnectionHint());
            return new NegotiationDecision(
                    dep.getDetectedProvider() != null ? dep.getDetectedProvider() : "external",
                    ProviderPreference.EXISTING_EXTERNAL,
                    OwnershipType.EXTERNAL,
                    90, "Production endpoint detected. Reusing existing infrastructure.",
                    evidence, rules, warnings);
        }

        // Priority 3: Repository has a development endpoint
        if (dep.isDevelopmentEndpoint()) {
            rules.add("RULE_3_DEVELOPMENT_ENDPOINT");
            evidence.add("Detected development-only endpoint: " + dep.getConnectionHint());
            warnings.add("Repository contains localhost/dev configuration. Deployrix will provision a replacement.");
            ProviderPreference fallback = inferDevFallback(dep.getType());
            return new NegotiationDecision(
                    resolveFallbackProvider(dep.getType()),
                    fallback,
                    OwnershipType.PLATFORM,
                    80, "Development endpoint detected. Recommending " + fallback + ".",
                    evidence, rules, warnings);
        }

        // Priority 4: No endpoint detected — platform defaults
        rules.add("RULE_4_PLATFORM_DEFAULT");
        evidence.add("No connection endpoint detected for " + dep.getType());
        ProviderPreference defaultPref = inferDevFallback(dep.getType());
        return new NegotiationDecision(
                resolveFallbackProvider(dep.getType()),
                defaultPref,
                OwnershipType.PLATFORM,
                60, "No endpoint detected. Using platform default: " + defaultPref + ".",
                evidence, rules, warnings);
    }

    private ProviderPreference inferDevFallback(String type) {
        switch (type.toUpperCase()) {
            case "MYSQL": case "POSTGRESQL": return ProviderPreference.PLATFORM_MANAGED;
            case "MONGODB": case "REDIS": case "KAFKA": case "RABBITMQ":
            case "ELASTICSEARCH": case "OPENSEARCH": case "MINIO":
                return ProviderPreference.DOCKER_RUNTIME;
            default: return ProviderPreference.DOCKER_RUNTIME;
        }
    }

    private String resolveProvider(String type, ProviderPreference pref) {
        if (pref == ProviderPreference.PLATFORM_MANAGED) return "aws_rds";
        if (pref == ProviderPreference.DOCKER_RUNTIME) return "docker";
        return "external";
    }

    private String resolveFallbackProvider(String type) {
        switch (type.toUpperCase()) {
            case "MYSQL": case "POSTGRESQL": return "aws_rds";
            case "MONGODB": return "docker_mongo";
            case "REDIS": return "docker_redis";
            default: return "docker";
        }
    }

    private String inferHealthStrategy(String type) {
        switch (type.toUpperCase()) {
            case "MYSQL": case "POSTGRESQL": return "SQL_QUERY";
            case "MONGODB": return "MONGO_PING";
            case "REDIS": return "REDIS_PING";
            case "KAFKA": return "KAFKA_METADATA";
            case "RABBITMQ": return "RABBITMQ_MANAGEMENT";
            case "ELASTICSEARCH": case "OPENSEARCH": return "HTTP";
            default: return "TCP";
        }
    }

    // ─── Internal types ────────────────────────────────────────

    private static class NegotiationDecision {
        final String provider;
        final ProviderPreference preference;
        final OwnershipType ownership;
        final int confidence;
        final String reason;
        final List<String> evidence;
        final List<String> rulesMatched;
        final List<String> warnings;

        NegotiationDecision(String provider, ProviderPreference preference, OwnershipType ownership,
                            int confidence, String reason, List<String> evidence,
                            List<String> rulesMatched, List<String> warnings) {
            this.provider = provider;
            this.preference = preference;
            this.ownership = ownership;
            this.confidence = confidence;
            this.reason = reason;
            this.evidence = evidence;
            this.rulesMatched = rulesMatched;
            this.warnings = warnings;
        }
    }

    @lombok.Value
    public static class NegotiationResult {
        List<DependencyContract> contracts;
        List<NegotiationReport> reports;
    }
}
