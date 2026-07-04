package com.autopilot.service.deployment.v5.runtime.dependency.adapter;

import com.autopilot.service.deployment.v5.negotiation.DependencyContract;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry auto-discovering all Spring-managed DependencyProviderAdapter implementations.
 * Zero switch statements.
 *
 * @since V5.4 — ADR-009
 */
@Service
public class DependencyProviderRegistry {

    private final List<DependencyProviderAdapter> adapters;

    public DependencyProviderRegistry(List<DependencyProviderAdapter> adapters) {
        this.adapters = adapters != null ? adapters : new ArrayList<>();
        System.out.println("🔌 Dependency Provider Registry initialized with "
                + this.adapters.size() + " dependency provider adapters.");
    }

    public DependencyProviderAdapter resolveAdapter(DependencyContract contract) {
        for (DependencyProviderAdapter adapter : adapters) {
            if (adapter.supports(contract)) {
                return adapter;
            }
        }
        String id = contract.getDependencyId() != null ? contract.getDependencyId() : contract.getType();
        throw new IllegalArgumentException("No dependency provider adapter found supporting provider: "
                + contract.getProvider() + " for dependency: " + id);
    }

    public List<DependencyProviderAdapter> getAllAdapters() {
        return Collections.unmodifiableList(adapters);
    }
}
