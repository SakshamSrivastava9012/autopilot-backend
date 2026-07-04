package com.autopilot.service.deployment.v5.migration.session;

import com.autopilot.dto.DeploymentManifest;
import com.autopilot.service.deployment.intelligence.v5.model.RepositoryModelV5;
import com.autopilot.service.deployment.v5.runtime.dependency.snapshot.DependencySnapshot;
import com.autopilot.service.deployment.v5.runtime.environment.snapshot.RuntimeEnvironmentSnapshot;
import com.autopilot.service.deployment.v5.runtime.infrastructure.snapshot.InfrastructureSnapshot;
import com.autopilot.service.deployment.v5.runtime.proxy.snapshot.ReverseProxySnapshot;
import com.autopilot.service.deployment.v5.runtime.startup.snapshot.RuntimeLifecycleSnapshot;
import com.autopilot.service.deployment.v5.runtime.verification.report.VerificationReports;
import com.autopilot.service.deployment.v5.runtime.verification.snapshot.VerificationSnapshot;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable root deployment session object representing the single source of truth for a deployment.
 * Every deployment has exactly one DeploymentSession.
 *
 * @since V5.5 — ADR-014
 */
@Value
@Builder
public class DeploymentSession {
    String deploymentId;
    RepositoryModelV5 repositoryModel;
    DeploymentManifest deploymentManifest;
    Object buildArtifact;
    InfrastructureSnapshot infrastructureSnapshot;
    DependencySnapshot dependencySnapshot;
    RuntimeEnvironmentSnapshot environmentSnapshot;
    RuntimeLifecycleSnapshot startupSnapshot;
    ReverseProxySnapshot reverseProxySnapshot;
    VerificationSnapshot verificationSnapshot;
    VerificationReports.DeploymentQualityReport deploymentQualityReport;
    List<String> timeline;
    Map<String, Object> reports;
}
