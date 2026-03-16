package com.autopilot.service.infrastructure.ec2;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.service.aws.AwsCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SSMDeployService {

    private final AwsCredentialService awsCredentialService;

    public void deployContainer(
            String instanceId,
            String image,
            int port,
            String region,
            String roleArn
    ) throws Exception {

        SsmClient ssmClient = buildSsmClient(roleArn, region);

        waitForSSM(ssmClient, instanceId);

        String registry = image.substring(0, image.indexOf('/'));

        String command = String.join(" && ",
                "echo 'Starting deployment'",
                "systemctl start docker",
                "docker --version",
                "aws ecr get-login-password --region " + region
                        + " | docker login --username AWS --password-stdin " + registry,
                "docker pull " + image,
                "docker rm -f autopilot-app || true",
                "docker run -d --name autopilot-app --restart unless-stopped -p "
                        + port + ":" + port + " " + image,
                "echo 'Container started successfully'"
        );

        System.out.println("Running SSM command on instance: " + instanceId);

        SendCommandRequest request = SendCommandRequest.builder()
                .instanceIds(instanceId)
                .documentName("AWS-RunShellScript")
                .parameters(java.util.Map.of("commands", List.of(command)))
                .build();

        SendCommandResponse response = ssmClient.sendCommand(request);
        String commandId = response.command().commandId();

        System.out.println("SSM command sent: " + commandId);

        waitForCommand(ssmClient, instanceId, commandId);
    }

    private SsmClient buildSsmClient(String roleArn, String region) throws Exception {

        AwsCredentialsDto creds = awsCredentialService.assumeRole(roleArn);

        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                creds.getAccessKeyId(),
                creds.getSecretAccessKey(),
                creds.getSessionToken()
        );

        return SsmClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                .build();
    }

    private void waitForSSM(SsmClient ssmClient, String instanceId) throws Exception {

        System.out.println("Waiting for SSM agent registration for: " + instanceId);

        for (int i = 0; i < 120; i++) {

            try {
                DescribeInstanceInformationRequest request =
                        DescribeInstanceInformationRequest.builder()
                                .filters(
                                        InstanceInformationStringFilter.builder()
                                                .key("InstanceIds")
                                                .values(instanceId)
                                                .build()
                                )
                                .build();

                DescribeInstanceInformationResponse response =
                        ssmClient.describeInstanceInformation(request);

                if (!response.instanceInformationList().isEmpty()) {

                    InstanceInformation info = response.instanceInformationList().get(0);
                    System.out.println("SSM ping status: " + info.pingStatus());

                    if (info.pingStatus() == PingStatus.ONLINE) {
                        System.out.println("SSM agent is ONLINE after " + (i * 5) + "s");
                        return;
                    }
                } else {
                    System.out.println("Instance not visible in SSM yet... attempt " + (i + 1) + "/120");
                }

            } catch (Exception e) {
                System.out.println("SSM describe error (will retry): " + e.getMessage());
            }

            Thread.sleep(5000);
        }

        throw new RuntimeException(
                "SSM agent never became ONLINE for instance: " + instanceId
                        + " after 10 minutes."
        );
    }

    private void waitForCommand(SsmClient ssmClient, String instanceId, String commandId) throws Exception {

        System.out.println("Waiting for deployment command to complete...");

        for (int i = 0; i < 120; i++) {

            try {
                GetCommandInvocationResponse response =
                        ssmClient.getCommandInvocation(
                                GetCommandInvocationRequest.builder()
                                        .commandId(commandId)
                                        .instanceId(instanceId)
                                        .build()
                        );

                System.out.println("Command status: " + response.status());

                if (response.status() == CommandInvocationStatus.SUCCESS) {
                    System.out.println("Deployment successful!");
                    System.out.println("Output:\n" + response.standardOutputContent());
                    return;
                }

                if (response.status() == CommandInvocationStatus.FAILED
                        || response.status() == CommandInvocationStatus.TIMED_OUT
                        || response.status() == CommandInvocationStatus.CANCELLED) {

                    System.out.println("STDOUT:\n" + response.standardOutputContent());
                    System.out.println("STDERR:\n" + response.standardErrorContent());
                    throw new RuntimeException(
                            "Deployment command failed with status: " + response.status()
                                    + "\nError: " + response.standardErrorContent()
                    );
                }

            } catch (InvocationDoesNotExistException e) {
                System.out.println("Command not propagated yet, waiting...");
            }

            Thread.sleep(4000);
        }

        throw new RuntimeException("Deployment command timed out after 8 minutes");
    }
}