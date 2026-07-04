package com.autopilot.service.deployment.v5.runtime.engine;

import com.autopilot.service.deployment.v5.runtime.contract.RuntimeContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry auto-discovering all Spring-managed RuntimeModule beans.
 *
 * @since V5.4 — ADR-007
 */
@Service
public class RuntimeModuleRegistry {

    private final List<RuntimeModule> modules;

    public RuntimeModuleRegistry(List<RuntimeModule> modules) {
        this.modules = modules != null ? modules : new ArrayList<>();
        System.out.println("🔌 V5 Runtime Module Registry initialized with " + this.modules.size() + " modules.");
    }

    public List<RuntimeModule> getSupportedModules(RuntimeContext context) {
        List<RuntimeModule> supported = new ArrayList<>();
        for (RuntimeModule module : modules) {
            if (module.supports(context)) {
                supported.add(module);
            }
        }
        return Collections.unmodifiableList(supported);
    }

    public List<RuntimeModule> getAllModules() {
        return Collections.unmodifiableList(modules);
    }
}
