package com.autopilot.service.infrastructure.ec2;

import com.autopilot.service.infrastructure.ssh.SshExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EC2DeployService {

    private final SshExecutor sshExecutor;

    public void deployContainer(
            String publicIp,
            String image,
            int containerPort,
            String region,
            String accountId,
            String privateKeyPath
    ) throws Exception {

        String host = publicIp;
        String user = "ubuntu";   // Ubuntu AMI user

        // wait for instance to boot
        Thread.sleep(60000);

        String ecrLoginCommand =
                "aws ecr get-login-password --region " + region +
                        " | docker login --username AWS --password-stdin " +
                        accountId + ".dkr.ecr." + region + ".amazonaws.com";

        sshExecutor.execute(host, user, privateKeyPath, ecrLoginCommand);

        String pullCommand = "docker pull " + image;

        sshExecutor.execute(host, user, privateKeyPath, pullCommand);

        String runCommand =
                "docker run -d -p " + containerPort + ":" + containerPort +
                        " --restart always " + image;

        sshExecutor.execute(host, user, privateKeyPath, runCommand);
    }
}
