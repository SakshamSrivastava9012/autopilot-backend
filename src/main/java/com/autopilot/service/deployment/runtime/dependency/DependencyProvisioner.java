package com.autopilot.service.deployment.runtime.dependency;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.service.aws.RdsProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class DependencyProvisioner {

    private final RdsProvisioningService rdsProvisioningService;

    public static class ProvisionResult {
        private final CredentialContract contract;
        private final List<String> preDeployCommands;

        public ProvisionResult(CredentialContract contract, List<String> preDeployCommands) {
            this.contract = contract;
            this.preDeployCommands = preDeployCommands;
        }

        public CredentialContract getContract() { return contract; }
        public List<String> getPreDeployCommands() { return preDeployCommands; }
    }

    public ProvisionResult provision(
            DependencyDescriptor descriptor,
            String negotiatedProvider,
            String deploymentId,
            AwsCredentialsDto creds,
            String region,
            Consumer<String> progressLog
    ) {
        System.out.println("🚀 Provisioning Dependency: " + descriptor.getName() + " via " + negotiatedProvider);
        
        List<String> preDeployCommands = new ArrayList<>();
        
        if ("EXISTING_EXTERNAL".equalsIgnoreCase(negotiatedProvider)) {
            CredentialContract contract = CredentialContract.parseUri(descriptor.getConnectionUri(), descriptor.getType());
            return new ProvisionResult(contract, preDeployCommands);
        }

        String dbType = descriptor.getType().toLowerCase();
        
        if ("PLATFORM_MANAGED".equalsIgnoreCase(negotiatedProvider)) {
            RdsProvisioningService.RdsResult rdsResult = rdsProvisioningService.provision(
                    dbType, deploymentId, creds, region, progressLog);
            if (rdsResult != null) {
                CredentialContract contract = CredentialContract.builder()
                        .provider(descriptor.getType().toUpperCase())
                        .host(rdsResult.endpoint())
                        .port(rdsResult.port())
                        .username(rdsResult.username())
                        .password(rdsResult.password())
                        .database(rdsResult.database())
                        .ssl(true)
                        .uri(rdsResult.envVars().get("SPRING_DATASOURCE_URL") != null ? rdsResult.envVars().get("SPRING_DATASOURCE_URL") : rdsResult.envVars().get("DATABASE_URL"))
                        .ownership("PLATFORM_MANAGED")
                        .generatedBy("AWS RDS")
                        .build();
                return new ProvisionResult(contract, preDeployCommands);
            }
            // Fallback to DOCKER_RUNTIME if RDS provisioning fails
            negotiatedProvider = "DOCKER_RUNTIME";
        }

        if ("DOCKER_RUNTIME".equalsIgnoreCase(negotiatedProvider)) {
            String dbName = "autopilotdb";
            if (descriptor.getConnectionUri() != null && !descriptor.getConnectionUri().isBlank()) {
                dbName = extractDatabaseName(descriptor.getConnectionUri(), "autopilotdb");
            }
            DependencyProvider provider = DependencyProviderFactory.create(dbType, dbName);
            ContainerId containerId = provider.start();
            StartupResult startupResult = provider.waitUntilReady();
            ConnectionInfo connInfo = provider.connectionInfo();

            preDeployCommands.addAll(startupResult.commands());

            CredentialContract contract = CredentialContract.builder()
                    .provider(descriptor.getType().toUpperCase())
                    .host(connInfo.host())
                    .port(connInfo.port())
                    .username(connInfo.username())
                    .password(connInfo.password())
                    .database(connInfo.database())
                    .ssl(false)
                    .uri(connInfo.uri())
                    .ownership("DOCKER_RUNTIME")
                    .generatedBy("Docker Engine")
                    .build();
            return new ProvisionResult(contract, preDeployCommands);
        }

        // Fallback for unsupported / simple external dependency contract
        CredentialContract contract = CredentialContract.builder()
                .provider(descriptor.getType().toUpperCase())
                .host("localhost")
                .port(8080)
                .ownership("USER_MANAGED")
                .build();
        return new ProvisionResult(contract, preDeployCommands);
    }

    private String extractDatabaseName(String uri, String defaultDb) {
        if (uri == null || uri.isBlank()) return defaultDb;
        try {
            String clean = uri;
            if (clean.startsWith("jdbc:")) {
                clean = clean.substring(5);
            }
            int doubleSlash = clean.indexOf("//");
            if (doubleSlash != -1) {
                clean = clean.substring(doubleSlash + 2);
            }
            
            int slash = clean.indexOf('/');
            if (slash == -1) return defaultDb;
            
            String path = clean.substring(slash + 1);
            int question = path.indexOf('?');
            if (question != -1) {
                path = path.substring(0, question);
            }
            
            String db = path.trim();
            if (db.isEmpty()) return defaultDb;
            return db;
        } catch (Exception e) {
            return defaultDb;
        }
    }
}
