package com.autopilot.service.deployment.v5.runtime.infrastructure.state;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe state store for tracking infrastructure resources created by Deployrix.
 * Similar to Terraform state — ensures deterministic cleanup and prevents orphaned resources.
 *
 * @since V5.4 — ADR-008
 */
@Service
public class InfrastructureResourceStateStore {

    private final Map<String, InfrastructureResourceStateRecord> stateStore = new ConcurrentHashMap<>();

    public void saveRecord(InfrastructureResourceStateRecord record) {
        if (record != null && record.getInternalResourceId() != null) {
            stateStore.put(record.getInternalResourceId(), record);
            System.out.println("💾 Infrastructure State Store — Persisted resource ["
                    + record.getInternalResourceId() + "] (cloudId=" + record.getCloudId()
                    + ", ownership=" + record.getOwnership() + ")");
        }
    }

    public Optional<InfrastructureResourceStateRecord> getRecord(String internalResourceId) {
        return Optional.ofNullable(stateStore.get(internalResourceId));
    }

    public List<InfrastructureResourceStateRecord> getRecordsByDeployment(String deploymentId) {
        List<InfrastructureResourceStateRecord> records = new ArrayList<>();
        for (InfrastructureResourceStateRecord r : stateStore.values()) {
            if (deploymentId != null && deploymentId.equals(r.getDeploymentId())) {
                records.add(r);
            }
        }
        return Collections.unmodifiableList(records);
    }

    public void deleteRecord(String internalResourceId) {
        stateStore.remove(internalResourceId);
    }

    public Map<String, InfrastructureResourceStateRecord> getAllRecords() {
        return Collections.unmodifiableMap(stateStore);
    }
}
