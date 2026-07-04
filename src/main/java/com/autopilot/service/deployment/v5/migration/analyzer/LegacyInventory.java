package com.autopilot.service.deployment.v5.migration.analyzer;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Inventory of legacy components, duplicated services, and DTOs.
 *
 * @since V5.5 — ADR-014
 */
@Value
@Builder
public class LegacyInventory {
    List<String> legacyServices;
    List<String> duplicatedDTOs;
    List<String> legacyReports;
    List<String> legacyValidators;
    List<String> legacyProxyGenerators;
    List<String> legacyEnvironmentInjectors;
}
