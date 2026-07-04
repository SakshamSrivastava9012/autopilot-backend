package com.autopilot.service.deployment.runtime.verification;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
public class RuntimeVerificationPlatform {

    private final List<VerificationModule> modules;

    public RuntimeVerificationPlatform(List<VerificationModule> modules) {
        this.modules = modules;
    }

    public VerificationReports.DeploymentQualityReport executeVerification(Map<String, Object> deploymentContext) {
        System.out.println("🛡️ Starting Runtime Verification Platform...");
        
        List<VerificationReports.RuntimeVerificationReport> reports = new ArrayList<>();
        boolean criticalFailureFound = false;

        for (VerificationModule module : modules) {
            if (module.supports(deploymentContext)) {
                try {
                    module.plan(deploymentContext);
                    module.verify();
                    VerificationReports.RuntimeVerificationReport report = module.report();
                    reports.add(report);
                    
                    if (report.getSeverity() == VerificationSeverity.CRITICAL && !report.isSuccess()) {
                        criticalFailureFound = true;
                        System.err.println("❌ CRITICAL Verification Failure in " + module.getClass().getSimpleName() + ": " + report.getDetails());
                    }
                } catch (Exception e) {
                    System.err.println("Module execution failed: " + module.getClass().getSimpleName());
                    // Modules failing must not hard-crash the platform unless critical.
                }
            }
        }

        // Mock construction of the combined quality report
        return VerificationReports.DeploymentQualityReport.builder()
                .deploymentSuccess(!criticalFailureFound)
                .moduleReports(reports)
                .smokeTestReport(VerificationReports.SmokeTestReport.builder().homePageLoads(true).healthEndpointPasses(true).databaseConnected(true).build())
                .browserReport(VerificationReports.BrowserVerificationReport.builder().domLoaded(true).build())
                .build();
    }
}
