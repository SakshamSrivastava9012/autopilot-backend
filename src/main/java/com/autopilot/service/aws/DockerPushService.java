package com.autopilot.service.aws;

import com.autopilot.dto.AwsCredentialsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class DockerPushService {

    private final AwsCredentialService awsCredentialService;

    public String pushImage(
            String roleArn,
            String region,
            String imageName
    ) throws Exception {

        // 1️⃣ Assume role
        AwsCredentialsDto creds =
                awsCredentialService.assumeRole(roleArn);

        AwsSessionCredentials sessionCredentials =
                AwsSessionCredentials.create(
                        creds.getAccessKeyId(),
                        creds.getSecretAccessKey(),
                        creds.getSessionToken()
                );

        EcrClient ecrClient = EcrClient.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(sessionCredentials)
                )
                .build();

        String repoUri;

        try {

            CreateRepositoryResponse response =
                    ecrClient.createRepository(
                            CreateRepositoryRequest.builder()
                                    .repositoryName(imageName)
                                    .build()
                    );

            repoUri = response.repository().repositoryUri();

        } catch (RepositoryAlreadyExistsException e) {

            DescribeRepositoriesResponse response =
                    ecrClient.describeRepositories(
                            DescribeRepositoriesRequest.builder()
                                    .repositoryNames(imageName)
                                    .build()
                    );

            repoUri = response.repositories().get(0).repositoryUri();
        }

        // 2️⃣ Get ECR login token
        GetAuthorizationTokenResponse authResponse =
                ecrClient.getAuthorizationToken();

        AuthorizationData authData =
                authResponse.authorizationData().get(0);

        String token =
                authData.authorizationToken();

        String decoded =
                new String(Base64.getDecoder().decode(token));

        String password =
                decoded.split(":")[1];

        String registry =
                authData.proxyEndpoint().replace("https://", "");

        // 3️⃣ Docker login
        runCommand(new String[]{
                "docker",
                "login",
                "-u",
                "AWS",
                "-p",
                password,
                registry
        });

        // 4️⃣ Tag image
        String fullImage =
                repoUri + ":latest";

        runCommand(new String[]{
                "docker",
                "tag",
                imageName,
                fullImage
        });

        // 5️⃣ Push image
        runCommand(new String[]{
                "docker",
                "push",
                fullImage
        });

        return fullImage;
    }

    private void runCommand(String[] command) throws Exception {

        Process process =
                Runtime.getRuntime().exec(command);

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        int exit = process.waitFor();

        if (exit != 0) {
            throw new RuntimeException("Docker command failed");
        }
    }
}
