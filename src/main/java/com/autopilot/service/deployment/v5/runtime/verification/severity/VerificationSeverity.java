package com.autopilot.service.deployment.v5.runtime.verification.severity;

/**
 * Verification severity classification.
 * Only CRITICAL fails a deployment.
 *
 * @since V5.4 — ADR-012
 */
public enum VerificationSeverity {
    INFO(false),
    WARNING(false),
    ERROR(false),
    CRITICAL(true);

    private final boolean fatal;

    VerificationSeverity(boolean fatal) {
        this.fatal = fatal;
    }

    public boolean isFatal() {
        return fatal;
    }
}
