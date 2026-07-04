package com.autopilot.service.deployment.runtime.verification;

import java.util.Map;

public interface VerificationModule {
    boolean supports(Map<String, Object> deploymentContext);
    void plan(Map<String, Object> deploymentContext);
    void verify();
    VerificationReports.RuntimeVerificationReport report();
}
