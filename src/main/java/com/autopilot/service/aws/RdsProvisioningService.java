package com.autopilot.service.aws;

import com.autopilot.dto.AwsCredentialsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.*;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Automatic RDS provisioning when Config Intelligence detects database dependencies.
 *
 * Provisions a managed database instance in the user's AWS account:
 * - MySQL → db.t3.micro MySQL 8.0
 * - Postgres → db.t3.micro PostgreSQL 15
 * - MongoDB → Skipped (DocumentDB requires VPC setup)
 *
 * Returns connection details as environment variables.
 */
@Service
@RequiredArgsConstructor
public class RdsProvisioningService {

    private final AwsCredentialService awsCredentialService;

    /**
     * Result of RDS provisioning.
     */
    public record RdsResult(
            String dbInstanceId,
            String engine,
            String endpoint,
            int port,
            String database,
            String username,
            String password,
            Map<String, String> envVars
    ) {}

    /**
     * Provision an RDS instance for the detected database type.
     *
     * @param dbType       "mysql" or "postgres"
     * @param deploymentId Unique deployment ID
     * @param roleArn      AWS IAM Role ARN
     * @param region       AWS Region
     * @param progressLog  Callback for real-time log messages (sent to frontend)
     * @return RdsResult with connection info and env vars, or null if unsupported
     */
    public RdsResult provision(String dbType, String deploymentId, String roleArn, String region,
                               Consumer<String> progressLog) {

        if (dbType == null) return null;

        String engine;
        String engineVersion;
        int dbPort;

        switch (dbType.toLowerCase()) {
            case "mysql" -> {
                engine = "mysql";
                engineVersion = "8.0";
                dbPort = 3306;
            }
            case "postgres" -> {
                engine = "postgres";
                engineVersion = "15";
                dbPort = 5432;
            }
            default -> {
                progressLog.accept("⚠️ RDS: Unsupported database type: " + dbType + " — skipping");
                return null;
            }
        }

        String shortId = deploymentId.replace("-", "").substring(0, 8);
        String dbInstanceId = "autopilot-" + shortId;
        String dbName = "autopilotdb";
        String masterUser = "autopilot";
        String masterPass = "AP" + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "!";

        try {
            RdsClient rdsClient = buildClient(roleArn, region);

            progressLog.accept("🗄️ RDS: Provisioning " + engine + " " + engineVersion + " instance...");

            try {
                CreateDbInstanceResponse response = rdsClient.createDBInstance(
                        CreateDbInstanceRequest.builder()
                                .dbInstanceIdentifier(dbInstanceId)
                                .dbInstanceClass("db.t3.micro")
                                .engine(engine)
                                .engineVersion(engineVersion)
                                .masterUsername(masterUser)
                                .masterUserPassword(masterPass)
                                .allocatedStorage(20)
                                .dbName(dbName)
                                .publiclyAccessible(true)
                                .storageType("gp3")
                                .backupRetentionPeriod(1)
                                .multiAZ(false)
                                .build()
                );

                String arn = response.dbInstance().dbInstanceArn();
                progressLog.accept("🗄️ RDS: Instance created → " + arn);
            } catch (DbInstanceAlreadyExistsException existing) {
                progressLog.accept("🗄️ RDS: Instance already exists — reusing and resetting password");
                
                // The instance might still be creating from a previous recent run.
                // We must wait for it to be 'available' before we can modify it.
                progressLog.accept("🗄️ RDS: Waiting for instance to become available...");
                waitForEndpoint(rdsClient, dbInstanceId, progressLog);
                
                rdsClient.modifyDBInstance(ModifyDbInstanceRequest.builder()
                        .dbInstanceIdentifier(dbInstanceId)
                        .masterUserPassword(masterPass)
                        .applyImmediately(true)
                        .build());
            }

            progressLog.accept("🗄️ RDS: Waiting for endpoint (this takes 3-5 minutes)...");

            // Wait for the instance to become available
            String endpoint = waitForEndpoint(rdsClient, dbInstanceId, progressLog);

            rdsClient.close();

            // Build connection URL
            String jdbcConnectionUrl;
            String genericConnectionUrl;
            if ("mysql".equals(engine)) {
                jdbcConnectionUrl = "jdbc:mysql://" + endpoint + ":" + dbPort + "/" + dbName
                        + "?allowPublicKeyRetrieval=true&useSSL=false&createDatabaseIfNotExist=true";
                genericConnectionUrl = "mysql://" + masterUser + ":" + masterPass + "@" + endpoint + ":" + dbPort + "/" + dbName;
            } else {
                jdbcConnectionUrl = "jdbc:postgresql://" + endpoint + ":" + dbPort + "/" + dbName;
                genericConnectionUrl = "postgresql://" + masterUser + ":" + masterPass + "@" + endpoint + ":" + dbPort + "/" + dbName;
            }

            // Build env var map with ALL possible variants
            Map<String, String> envVars = Map.ofEntries(
                    Map.entry("DATABASE_URL", genericConnectionUrl),
                    Map.entry("DB_HOST", endpoint),
                    Map.entry("DB_PORT", String.valueOf(dbPort)),
                    Map.entry("DB_NAME", dbName),
                    Map.entry("DB_USER", masterUser),
                    Map.entry("DB_PASSWORD", masterPass),
                    Map.entry("SPRING_DATASOURCE_URL", jdbcConnectionUrl),
                    Map.entry("SPRING_DATASOURCE_USERNAME", masterUser),
                    Map.entry("SPRING_DATASOURCE_PASSWORD", masterPass)
            );

            progressLog.accept("🗄️ RDS: ✅ Ready at " + endpoint + ":" + dbPort);

            return new RdsResult(dbInstanceId, engine, endpoint, dbPort, dbName, masterUser, masterPass, envVars);

        } catch (Exception e) {
            progressLog.accept("⚠️ RDS: Provisioning failed — " + e.getMessage());
            // Non-fatal — deployment continues without managed database
            return null;
        }
    }

    /**
     * Wait for the RDS instance to get an endpoint (up to 10 minutes).
     * Emits progress every 30 seconds so the frontend doesn't appear stuck.
     */
    private String waitForEndpoint(RdsClient rdsClient, String dbInstanceId,
                                   Consumer<String> progressLog) throws Exception {
        for (int i = 0; i < 60; i++) {
            try {
                DescribeDbInstancesResponse response = rdsClient.describeDBInstances(
                        DescribeDbInstancesRequest.builder()
                                .dbInstanceIdentifier(dbInstanceId)
                                .build()
                );

                DBInstance instance = response.dbInstances().get(0);
                String status = instance.dbInstanceStatus();

                // Log progress every 30 seconds (every 3rd iteration)
                if (i > 0 && i % 3 == 0) {
                    int elapsed = i * 10;
                    progressLog.accept("🗄️ RDS: Still provisioning... status=" + status
                            + " (" + elapsed + "s elapsed)");
                }

                if ("available".equals(status) && 
                    instance.endpoint() != null && 
                    instance.endpoint().address() != null) {
                    return instance.endpoint().address();
                }
            } catch (Exception ignored) {}

            Thread.sleep(10_000); // check every 10 seconds
        }

        throw new RuntimeException("RDS endpoint not available after 10 minutes");
    }

    private RdsClient buildClient(String roleArn, String region) throws Exception {
        AwsCredentialsDto creds = awsCredentialService.assumeRole(roleArn);

        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                creds.getAccessKeyId(),
                creds.getSecretAccessKey(),
                creds.getSessionToken()
        );

        return RdsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                .build();
    }
}
