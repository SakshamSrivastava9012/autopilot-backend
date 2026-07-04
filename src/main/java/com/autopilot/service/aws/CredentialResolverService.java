package com.autopilot.service.aws;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.entity.Deployment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialResolverService {

    private final AwsCredentialService awsCredentialService;

    @Value("${autopilot.platform.role-arn:}")
    private String platformRoleArn;

    @Value("${autopilot.platform.region:ap-south-1}")
    private String platformRegion;

    public record ResolvedCredentials(
            AwsCredentialsDto credentials,
            String region,
            String roleArn,
            boolean isAssumedRole
    ) {}

    public ResolvedCredentials resolve(Deployment deployment) {
        String mode = deployment.getDeploymentMode();
        if (mode == null || mode.isBlank()) {
            mode = "BYOC";
        }
        mode = mode.toUpperCase();

        switch (mode) {
            case "MANAGED":
                if (platformRoleArn == null || platformRoleArn.isBlank()) {
                    log.info("MANAGED mode active but platform.role-arn is empty. Using local system credentials directly.");
                    return new ResolvedCredentials(null, platformRegion, null, false);
                } else {
                    log.info("MANAGED mode: Assuming platform role {}", platformRoleArn);
                    AwsCredentialsDto creds = awsCredentialService.assumeRole(platformRoleArn);
                    return new ResolvedCredentials(creds, platformRegion, platformRoleArn, true);
                }
            case "BYOC":
                log.info("BYOC mode: Assuming user role {}", deployment.getAwsRoleArn());
                AwsCredentialsDto creds = awsCredentialService.assumeRole(deployment.getAwsRoleArn());
                return new ResolvedCredentials(creds, deployment.getAwsRegion(), deployment.getAwsRoleArn(), true);
            default:
                throw new IllegalArgumentException("Unsupported deployment mode: " + mode);
        }
    }
}
