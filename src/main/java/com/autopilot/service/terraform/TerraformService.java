package com.autopilot.service.terraform;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.service.aws.AwsCredentialService;
import com.autopilot.service.infrastructure.CapacityPlanner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.*;

@Service
@RequiredArgsConstructor
public class TerraformService {

    private final AwsCredentialService awsCredentialService;
    private final CapacityPlanner capacityPlanner;

    private static final String TERRAFORM_ROOT =
            "/tmp/autopilot-terraform";

    public String provisionInfrastructure(
            String roleArn,
            String region,
            Integer expectedUsers,
            int appPort,
            String deploymentId
    ) throws Exception {

        AwsCredentialsDto creds =
                awsCredentialService.assumeRole(roleArn);

        String instanceType =
                capacityPlanner.chooseInstanceType(expectedUsers);

        Path terraformDir =
                Path.of(TERRAFORM_ROOT, deploymentId);

        if (Files.exists(terraformDir)) {
            deleteDirectory(terraformDir);
        }

        Files.createDirectories(terraformDir);

        Path templateDir =
                Path.of("src/main/resources/terraform");

        Files.walk(templateDir).forEach(source -> {
            try {

                Path destination =
                        terraformDir.resolve(
                                templateDir.relativize(source)
                        );

                if (Files.isDirectory(source)) {

                    Files.createDirectories(destination);

                } else {

                    Files.copy(
                            source,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Path tfvars =
                terraformDir.resolve("terraform.tfvars");

        String content =
                "region=\"" + region + "\"\n" +
                        "access_key=\"" + creds.getAccessKeyId() + "\"\n" +
                        "secret_key=\"" + creds.getSecretAccessKey() + "\"\n" +
                        "session_token=\"" + creds.getSessionToken() + "\"\n" +
                        "instance_type=\"" + instanceType + "\"\n" +
                        "app_port=" + appPort + "\n" +
                        "deployment_id=\"" + deploymentId + "\"\n" +
                        "ami_id=\"ami-0f5ee92e2d63afc18\"";

        Files.writeString(tfvars, content);

        run(terraformDir,"terraform","init","-upgrade");
        run(terraformDir,"terraform","apply","-auto-approve");

        String instanceId =
                run(terraformDir,"terraform","output","-raw","instance_id");

        return instanceId.trim();
    }

    private String run(Path dir,String... cmd) throws Exception {

        Process p =
                new ProcessBuilder(cmd)
                        .directory(dir.toFile())
                        .redirectErrorStream(true)
                        .start();

        BufferedReader r =
                new BufferedReader(
                        new InputStreamReader(p.getInputStream())
                );

        StringBuilder out=new StringBuilder();
        String line;

        while((line=r.readLine())!=null){

            System.out.println(line);
            out.append(line).append("\n");
        }

        int exit=p.waitFor();

        if(exit!=0){

            throw new RuntimeException("Terraform failed:\n"+out);
        }

        return out.toString();
    }

    private void deleteDirectory(Path path)throws Exception{

        if(!Files.exists(path))return;

        Files.walk(path)
                .sorted((a,b)->b.compareTo(a))
                .forEach(p->p.toFile().delete());
    }
}
