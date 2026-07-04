package com.autopilot.service.deployment.v5.migration.engine;

import com.autopilot.service.deployment.v5.migration.analyzer.LegacyAnalyzer;
import com.autopilot.service.deployment.v5.migration.analyzer.LegacyInventory;
import com.autopilot.service.deployment.v5.migration.cleanup.DeadCodeDetector;
import com.autopilot.service.deployment.v5.migration.matrix.ReplacementMatrix;
import com.autopilot.service.deployment.v5.migration.planner.MigrationPlanner;
import com.autopilot.service.deployment.v5.migration.report.MigrationReports;
import com.autopilot.service.deployment.v5.migration.validation.MigrationValidator;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Universal Platform Migration & Legacy Elimination Engine V5.
 *
 * Coordinates transition from legacy pipeline to single-engine V5 production runtime.
 * Never executes deployments directly.
 *
 * @since V5.5 — ADR-014
 */
@Service
public class PlatformMigrationEngine {

    private final LegacyAnalyzer analyzer;
    private final ReplacementMatrix matrix;
    private final DeadCodeDetector deadCodeDetector;
    private final MigrationValidator migrationValidator;
    private final MigrationPlanner migrationPlanner;

    public PlatformMigrationEngine(LegacyAnalyzer analyzer,
                                  ReplacementMatrix matrix,
                                  DeadCodeDetector deadCodeDetector,
                                  MigrationValidator migrationValidator,
                                  MigrationPlanner migrationPlanner) {
        this.analyzer = analyzer;
        this.matrix = matrix;
        this.deadCodeDetector = deadCodeDetector;
        this.migrationValidator = migrationValidator;
        this.migrationPlanner = migrationPlanner;
        System.out.println("🚀 Platform Migration Engine V5 active — Delegating legacy API calls to single V5 Runtime Engine.");
    }

    public MigrationReports.MigrationReport runMigrationAnalysis() {
        System.out.println("📊 Platform Migration Engine — Running migration analysis...");

        LegacyInventory inventory = analyzer.analyzeCodebase();
        List<String> deadCode = deadCodeDetector.detectDeadCode();
        boolean parity = migrationValidator.validateFeatureParity();
        List<ReplacementMatrix.MappingEntry> plan = migrationPlanner.planMigration();

        return MigrationReports.MigrationReport.builder()
                .status("SINGLE_ENGINE_MIGRATED")
                .migratedComponentsCount(plan.size())
                .matrix(plan)
                .deadCodeCandidates(deadCode)
                .featureParityConfirmed(parity)
                .build();
    }
}
