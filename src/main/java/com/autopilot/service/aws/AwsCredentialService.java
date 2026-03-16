package com.autopilot.service.aws;

import com.autopilot.dto.AwsCredentialsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

@Service
@RequiredArgsConstructor
public class AwsCredentialService {

    private final StsClient stsClient;

    public AwsCredentialsDto assumeRole(String roleArn) {

        AssumeRoleRequest request = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName("autopilot-session")
                .durationSeconds(3600)
                .build();

        AssumeRoleResponse response = stsClient.assumeRole(request);

        Credentials credentials = response.credentials();

        return new AwsCredentialsDto(
                credentials.accessKeyId(),
                credentials.secretAccessKey(),
                credentials.sessionToken()
        );
    }
}