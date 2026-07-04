package com.autopilot.service.deployment.module;

import com.autopilot.dto.DeploymentManifest;

import java.util.List;

/**
 * Core interface for the Capability-Driven Deployment Engine.
 * Replaces the monolithic deployment pipeline with modular, idempotent execution.
 */
public interface CompatibilityModule {

    /**
     * Determines if this module should execute based on the current manifest.
     * @param manifest The current immutable deployment manifest.
     * @return true if the capability applies to this deployment, false otherwise.
     */
    boolean supports(DeploymentManifest manifest);

    /**
     * Calculates required changes without actively applying side effects.
     * @param manifest The current immutable deployment manifest.
     * @return A list of Operations representing the plan.
     */
    List<Operation> plan(DeploymentManifest manifest);

    /**
     * Executes the planned operations.
     * @param operations The operations returned by plan().
     */
    void apply(List<Operation> operations);

    /**
     * Verifies that the applied changes conform to the capability requirements.
     * E.g., checks if static assets return 200/304, or if DB is reachable.
     */
    VerificationResult verify();

    /**
     * Rolls back changes if verification or subsequent modules fail.
     */
    void rollback();
}
