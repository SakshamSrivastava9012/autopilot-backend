package com.autopilot.service.deployment.validation;

import com.autopilot.dto.DeployedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DeploymentValidationSuite {

    private final CapabilityHealthVerifier healthVerifier;
    private final CapabilityAssetVerifier assetVerifier;
    private final CapabilityOAuthVerifier oauthVerifier;
    private final CapabilityBrowserVerifier browserVerifier;

    public ValidationResult runSuite(com.autopilot.dto.DeploymentManifest manifest, List<DeployedService> services, String publicIp, String mainAccessUrl) {
        List<String> errors = new ArrayList<>();

        for (DeployedService ds : services) {
            // 1. Health Verification
            boolean healthOk = healthVerifier.verifyHealth(ds, publicIp, manifest);
            if (!healthOk) {
                errors.add("Service " + ds.getName() + " health check failed at " + (ds.getHealthPath() != null ? ds.getHealthPath() : "/"));
            }

            // 2. Asset Verification (Recursive Graph)
            String serviceAccessUrl = null;
            if (ds.getRuntimeContract() != null && ds.getRuntimeContract().getExternalBrowserUrl() != null) {
                serviceAccessUrl = ds.getRuntimeContract().getExternalBrowserUrl();
            } else {
                serviceAccessUrl = "http://" + publicIp + ds.getBasePath();
            }
            if (!serviceAccessUrl.endsWith("/")) {
                serviceAccessUrl += "/";
            }

            List<String> assetErrors = assetVerifier.verifyAssets(ds, publicIp, serviceAccessUrl, manifest);
            errors.addAll(assetErrors);

            // 3. Browser-Level Execution Verification
            boolean isFrontend = "frontend".equalsIgnoreCase(ds.getRole()) || 
                               "SPA".equalsIgnoreCase(String.valueOf(ds.getRole())) ||
                               "STATIC_SITE".equalsIgnoreCase(String.valueOf(ds.getRole())) ||
                               "SSR".equalsIgnoreCase(String.valueOf(ds.getRole()));
            if (isFrontend) {
                List<String> browserErrors = browserVerifier.verifyInBrowser(ds, publicIp, serviceAccessUrl);
                errors.addAll(browserErrors);
            }

            // 4. OAuth Verification
            boolean oauthOk = oauthVerifier.verifyOAuthSetup(ds, publicIp, serviceAccessUrl);
            if (!oauthOk) {
                errors.add("Service " + ds.getName() + " OAuth verification failed.");
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public static class ValidationResult {
        private final boolean success;
        private final List<String> errors;

        public ValidationResult(boolean success, List<String> errors) {
            this.success = success;
            this.errors = errors;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
