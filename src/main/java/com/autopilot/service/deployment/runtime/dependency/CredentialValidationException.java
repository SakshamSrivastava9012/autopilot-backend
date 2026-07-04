package com.autopilot.service.deployment.runtime.dependency;

public class CredentialValidationException extends RuntimeException {
    private final DependencyReports.CredentialValidationReport report;

    public CredentialValidationException(DependencyReports.CredentialValidationReport report) {
        super("Database credential verification failed: " + report.getFailureType() + " - " + report.getRootCause());
        this.report = report;
    }

    public DependencyReports.CredentialValidationReport getReport() {
        return report;
    }
}
