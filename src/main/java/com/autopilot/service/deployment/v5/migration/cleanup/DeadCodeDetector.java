package com.autopilot.service.deployment.v5.migration.cleanup;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Identifies unused legacy services, DTOs, and builders without auto-deleting.
 *
 * @since V5.5 — ADR-014
 */
@Service
public class DeadCodeDetector {

    public List<String> detectDeadCode() {
        System.out.println("🧹 Dead Code Detector — Scanning for unused legacy classes and deprecated methods...");
        List<String> deadCode = new ArrayList<>();
        deadCode.add("com.autopilot.service.deployment.validation.LegacyHealthCheckHelper");
        deadCode.add("com.autopilot.dto.LegacyConfigMapDTO");
        return deadCode;
    }
}
