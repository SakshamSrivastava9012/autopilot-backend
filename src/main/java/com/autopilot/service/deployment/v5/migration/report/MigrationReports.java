package com.autopilot.service.deployment.v5.migration.report;

import com.autopilot.service.deployment.v5.migration.matrix.ReplacementMatrix;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Dashboard-ready migration reports.
 *
 * @since V5.5 — ADR-014
 */
public class MigrationReports {

    @Value
    @Builder
    public static class MigrationReport {
        String status;
        int migratedComponentsCount;
        List<ReplacementMatrix.MappingEntry> matrix;
        List<String> deadCodeCandidates;
        boolean featureParityConfirmed;
    }

    @Value
    @Builder
    public static class CompatibilityReport {
        boolean adapterModeActive;
        String activeProfile;
        List<String> delegatedLegacyAPIs;
    }
}
