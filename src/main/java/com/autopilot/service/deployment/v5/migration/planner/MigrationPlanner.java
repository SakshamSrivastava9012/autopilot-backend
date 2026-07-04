package com.autopilot.service.deployment.v5.migration.planner;

import com.autopilot.service.deployment.v5.migration.matrix.ReplacementMatrix;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Migration Planner — plans orderly migration steps from legacy components to V5 runtime engines.
 *
 * @since V5.5 — ADR-014
 */
@Service
public class MigrationPlanner {

    private final ReplacementMatrix matrix;

    public MigrationPlanner(ReplacementMatrix matrix) {
        this.matrix = matrix;
    }

    public List<ReplacementMatrix.MappingEntry> planMigration() {
        System.out.println("📋 Migration Planner — Planning migration sequence for " + matrix.getMappings().size() + " legacy services...");
        return matrix.getMappings();
    }
}
