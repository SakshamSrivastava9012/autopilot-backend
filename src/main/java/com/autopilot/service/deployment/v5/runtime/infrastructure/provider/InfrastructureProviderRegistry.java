package com.autopilot.service.deployment.v5.runtime.infrastructure.provider;

import com.autopilot.service.deployment.v5.runtime.infrastructure.contract.InfrastructureContract;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry auto-discovering all Spring-managed InfrastructureProviderAdapter implementations.
 * Zero switch statements.
 *
 * @since V5.4 — ADR-008
 */
@Service
public class InfrastructureProviderRegistry {

    private final List<InfrastructureProviderAdapter> adapters;

    public InfrastructureProviderRegistry(List<InfrastructureProviderAdapter> adapters) {
        this.adapters = adapters != null ? adapters : new ArrayList<>();
        System.out.println("🔌 Infrastructure Provider Registry initialized with "
                + this.adapters.size() + " provider adapters.");
    }

    public InfrastructureProviderAdapter resolveAdapter(InfrastructureContract contract) {
        for (InfrastructureProviderAdapter adapter : adapters) {
            if (adapter.supports(contract)) {
                return adapter;
            }
        }
        throw new IllegalArgumentException("No infrastructure provider adapter found supporting contract provider: "
                + contract.getProvider() + " and resource type: " + contract.getResourceType());
    }

    public List<InfrastructureProviderAdapter> getAllAdapters() {
        return Collections.unmodifiableList(adapters);
    }
}
