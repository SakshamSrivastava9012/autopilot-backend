package com.autopilot.service.terraform;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.dto.TerraformResult;
import com.autopilot.service.infrastructure.CapacityPlanner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TerraformService {

    private final CapacityPlanner capacityPlanner;

    private static final String TERRAFORM_ROOT = "/tmp/autopilot-terraform";

    public TerraformResult provisionInfrastructure(
            AwsCredentialsDto creds,
            String region,
            String instanceType,
            int appPort,
            String deploymentId,
            String rdsSecurityGroupId,
            Integer rdsPort
    ) throws Exception {

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

        Path tfvars = terraformDir.resolve("terraform.tfvars");

        // Ubuntu 22.04 LTS AMIs per region (amd64, hvm:ebs-ssd)
        // These should be updated periodically or replaced with dynamic lookup
        String amiId = getUbuntuAmi(region);

        String accessKeyVal = creds != null ? creds.getAccessKeyId() : "";
        String secretKeyVal = creds != null ? creds.getSecretAccessKey() : "";
        String sessionTokenVal = creds != null ? creds.getSessionToken() : "";

        String content =
                "region=\"" + region + "\"\n" +
                        "access_key=\"" + accessKeyVal + "\"\n" +
                        "secret_key=\"" + secretKeyVal + "\"\n" +
                        "session_token=\"" + sessionTokenVal + "\"\n" +
                        "instance_type=\"" + instanceType + "\"\n" +
                        "app_port=" + appPort + "\n" +
                        "deployment_id=\"" + deploymentId + "\"\n" +
                        "ami_id=\"" + amiId + "\"\n" +
                        "rds_security_group_id=\"" + (rdsSecurityGroupId != null ? rdsSecurityGroupId : "") + "\"\n" +
                        "rds_port=" + (rdsPort != null ? rdsPort : 3306);

        Files.writeString(tfvars, content);

        // ✅ NO -upgrade (important)
        runWithRetry(terraformDir, "terraform", "init");
        runWithRetry(terraformDir, "terraform", "apply", "-auto-approve");

        String instanceIdRaw =
                run(terraformDir, "terraform", "output", "-raw", "instance_id");

        String publicIpRaw =
                run(terraformDir, "terraform", "output", "-raw", "public_ip");

// 🔥 CLEAN OUTPUT (remove terraform warnings)
        String instanceId = extractValue(instanceIdRaw);
        String publicIp = extractValue(publicIpRaw);
        TerraformResult result = new TerraformResult();
        result.setInstanceId(instanceId);
        result.setPublicIp(publicIp);

        return result;
    }

    // 🔥 Retry wrapper (production reliability)
    private String runWithRetry(Path dir, String... cmd) throws Exception {
        int attempts = 3;

        for (int i = 1; i <= attempts; i++) {
            try {
                return run(dir, cmd);
            } catch (Exception e) {
                if (i == attempts) throw e;

                System.out.println("[TERRAFORM] Retry attempt " + i);
                Thread.sleep(3000);
            }
        }
        throw new RuntimeException("Terraform failed after retries");
    }

    private String run(Path dir, String... cmd) throws Exception {

        ProcessBuilder pb = new ProcessBuilder(cmd);

        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();
        
        // 🛑 CRITICAL PREVENTION OF `/tmp` TMPFS QUOTA EXHAUSTION
        // By using a global terraform plugin cache, we download the 350MB+ Hashicorp plugins exactly once
        // and magically symlink them into every isolated /tmp/autopilot-terraform/<uuid> workspace directory.
        Path pluginCache = Path.of("/tmp/terraform-plugin-cache");
        if (!Files.exists(pluginCache)) {
            Files.createDirectories(pluginCache);
        }
        env.put("TF_PLUGIN_CACHE_DIR", pluginCache.toString());
        
        // Removed hardcoded /home/saksham/.terraformrc to fix 'Warning: Unable to open CLI configuration file'
        
        Process p = pb.start();

        BufferedReader r =
                new BufferedReader(
                        new InputStreamReader(p.getInputStream())
                );

        StringBuilder out = new StringBuilder();
        String line;

        while ((line = r.readLine()) != null) {
            System.out.println("[TERRAFORM] " + line);
            out.append(line).append("\n");
        }

        int exit = p.waitFor();

        if (exit != 0) {
            throw new RuntimeException("Terraform failed:\n" + out);
        }

        return out.toString();
    }

    public void destroyInfrastructure(
            AwsCredentialsDto creds,
            String region,
            int appPort,
            String deploymentId
    ) throws Exception {

        Path terraformDir =
                Path.of(TERRAFORM_ROOT, deploymentId);

        // Recreate directory and copy templates if missing (e.g. reboot/cleanup)
        if (!Files.exists(terraformDir)) {
            Files.createDirectories(terraformDir);
            Path templateDir = Path.of("src/main/resources/terraform");
            Files.walk(templateDir).forEach(source -> {
                try {
                    Path destination = terraformDir.resolve(templateDir.relativize(source));
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        Path tfvars = terraformDir.resolve("terraform.tfvars");

        String accessKeyVal = creds != null ? creds.getAccessKeyId() : "";
        String secretKeyVal = creds != null ? creds.getSecretAccessKey() : "";
        String sessionTokenVal = creds != null ? creds.getSessionToken() : "";
        String amiId = getUbuntuAmi(region);

        String content =
                "region=\"" + region + "\"\n" +
                        "access_key=\"" + accessKeyVal + "\"\n" +
                        "secret_key=\"" + secretKeyVal + "\"\n" +
                        "session_token=\"" + sessionTokenVal + "\"\n" +
                        "instance_type=\"t3.micro\"\n" +
                        "app_port=" + appPort + "\n" +
                        "deployment_id=\"" + deploymentId + "\"\n" +
                        "ami_id=\"" + amiId + "\"";

        Files.writeString(tfvars, content);

        runWithRetry(terraformDir, "terraform", "init");
        runWithRetry(terraformDir, "terraform", "destroy", "-auto-approve");

        deleteDirectory(terraformDir);
    }

    public void cleanupWorkspace(String deploymentId) {
        try {
            Path terraformDir = Path.of(TERRAFORM_ROOT, deploymentId);
            deleteDirectory(terraformDir);
        } catch (Exception e) {
            System.err.println("Failed to cleanup terraform workspace for " + deploymentId + ": " + e.getMessage());
        }
    }

    private void deleteDirectory(Path path) throws Exception {

        if (!Files.exists(path)) return;

        Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> p.toFile().delete());
    }
    private String extractValue(String raw) {
        if (raw == null) return null;

        String[] lines = raw.split("\n");

        for (String line : lines) {
            line = line.trim();

            // skip empty & warning lines
            if (line.isEmpty()) continue;
            if (line.contains("Warning")) continue;
            if (line.contains("There are some problems")) continue;
            if (line.startsWith("?")) continue;

            // valid value line (instance id or ip)
            if (line.matches("i-[a-zA-Z0-9]+") || line.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                return line;
            }
        }

        // fallback (last non-empty line)
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return line;
            }
        }

        throw new RuntimeException("Failed to extract terraform output from:\n" + raw);
    }

    /**
     * Ubuntu 22.04 LTS AMI IDs per AWS region (amd64, hvm:ebs-ssd).
     * Updated: June 2026. For production, replace with dynamic EC2 DescribeImages lookup.
     */
    private static final Map<String, String> UBUNTU_AMIS = Map.ofEntries(
            Map.entry("ap-south-1",    "ami-0f5ee92e2d63afc18"),
            Map.entry("us-east-1",     "ami-0c7217cdde317cfec"),
            Map.entry("us-east-2",     "ami-05fb0b8c1424f266b"),
            Map.entry("us-west-1",     "ami-0ce2cb35386fc22e9"),
            Map.entry("us-west-2",     "ami-008fe2fc65df48dac"),
            Map.entry("eu-west-1",     "ami-0905a3c97561e0b69"),
            Map.entry("eu-central-1",  "ami-0faab6bdbac9486fb"),
            Map.entry("ap-southeast-1","ami-078c1149d8ad719a7")
    );

    private String getUbuntuAmi(String region) {
        String ami = UBUNTU_AMIS.get(region);
        if (ami != null) return ami;

        System.err.println("⚠️ No cached AMI for region " + region + " — falling back to ap-south-1 AMI");
        return UBUNTU_AMIS.get("ap-south-1");
    }
}