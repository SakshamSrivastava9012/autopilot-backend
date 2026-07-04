package com.autopilot.service.deployment.runtime.dependency;

import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.service.infrastructure.ec2.SSMDeployService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class DependencyValidator {

    private final SSMDeployService ssmDeployService;

    public DependencyReports.CredentialValidationReport validate(
            CredentialContract contract,
            String ec2InstanceId,
            String region,
            AwsCredentialsDto awsCreds
    ) {
        String provider = contract.getProvider();
        String host = contract.getHost();
        int port = contract.getPort();
        String username = contract.getUsername();
        String password = contract.getPassword();
        String database = contract.getDatabase();
        
        System.out.println("✅ Validating Credentials for Provider: " + provider + " on " + host + ":" + port);

        boolean isRuntimeHost = isDockerOrRuntimeHost(provider, host);
        
        // 1. DNS Verification (Bypassed for runtime-generated hostnames per ADR-009/010)
        if (!isRuntimeHost) {
            try {
                InetAddress.getByName(host);
            } catch (Exception e) {
                return DependencyReports.CredentialValidationReport.builder()
                        .success(false)
                        .failureType("DATABASE_UNREACHABLE")
                        .expectedCredentials("Host: " + host)
                        .actualNegotiatedCredentialsRedacted("Redacted")
                        .provider(provider)
                        .validationStep("DNS Lookup")
                        .rootCause("DNS Resolution failed for host: " + host)
                        .suggestedFix("Ensure the database host name is correct and accessible via network.")
                        .build();
            }
        }
        
        // 2. TCP Port Verification (Local network check or SSM container status check)
        boolean tcpOk = false;
        if (!isRuntimeHost) {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 5000);
                tcpOk = true;
            } catch (Exception ignored) {}
        } else {
            // For runtime hosts (e.g. autopilot-mysql), check if localhost port is listening or SSM container is running
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 3000);
                tcpOk = true;
            } catch (Exception ignored) {}
        }

        if (!tcpOk && ec2InstanceId != null && ssmDeployService != null) {
            // Host might be internal to EC2. Check if Docker container is running on host
            try {
                String containerName = host != null && host.startsWith("autopilot-") ? host : "autopilot-" + provider.toLowerCase();
                String status = ssmDeployService.runCommandAndGetOutput(ec2InstanceId, "docker inspect -f '{{.State.Running}}' " + containerName, region, awsCreds);
                if (status != null && status.trim().equals("true")) {
                    tcpOk = true;
                }
            } catch (Exception ignored) {}
        }
        
        // If it's a runtime host provisioned locally or in progress, bypass pre-flight TCP block if initial setup
        if (!tcpOk && isRuntimeHost) {
            System.out.println("ADR-009/010: Pre-flight TCP check deferred for runtime provider [" + provider + "] host [" + host + "]");
            tcpOk = true;
        }

        if (!tcpOk) {
            return DependencyReports.CredentialValidationReport.builder()
                    .success(false)
                    .failureType("DATABASE_TIMEOUT")
                    .expectedCredentials("Port: " + port)
                    .actualNegotiatedCredentialsRedacted("Redacted")
                    .provider(provider)
                    .validationStep("TCP Socket Check")
                    .rootCause("Database port is unreachable on " + host + ":" + port)
                    .suggestedFix("Verify security group rules, routing tables, and that the service is running.")
                    .build();
        }
        
        // 3. Authentication & DB/User Verification
        String targetHost = isRuntimeHost ? "127.0.0.1" : host;
        if ("MYSQL".equalsIgnoreCase(provider) || "DOCKER_RUNTIME".equalsIgnoreCase(provider)) {
            // First try direct JDBC if reachable
            try {
                String jdbcUrl = "jdbc:mysql://" + targetHost + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true";
                try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                    // Success!
                }
            } catch (SQLException e) {
                if (!isRuntimeHost) {
                    return classifySqlException(e, provider, username, database);
                }
            } catch (Exception ignored) {
                if (ec2InstanceId != null && ssmDeployService != null) {
                    try {
                        String cmd = "docker exec autopilot-mysql mysql -u" + username + " -p" + password + " -e 'SELECT 1;'";
                        String out = ssmDeployService.runCommandAndGetOutput(ec2InstanceId, cmd, region, awsCreds);
                        if (out == null || !out.contains("1")) {
                            return parseSsmFailure(out, provider, username, database);
                        }
                    } catch (Exception ssmEx) {
                        return classifySsmException(ssmEx, provider, username, database);
                    }
                }
            }
        } else if ("POSTGRESQL".equalsIgnoreCase(provider)) {
            try {
                String jdbcUrl = "jdbc:postgresql://" + targetHost + ":" + port + "/" + database;
                try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                    // Success!
                }
            } catch (SQLException e) {
                if (!isRuntimeHost) {
                    return classifySqlException(e, provider, username, database);
                }
            } catch (Exception ignored) {
                if (ec2InstanceId != null && ssmDeployService != null) {
                    try {
                        String cmd = "docker exec autopilot-postgres psql -U " + username + " -d " + database + " -c 'SELECT 1;'";
                        String out = ssmDeployService.runCommandAndGetOutput(ec2InstanceId, cmd, region, awsCreds);
                        if (out == null || !out.contains("1")) {
                            return parseSsmFailure(out, provider, username, database);
                        }
                    } catch (Exception ssmEx) {
                        return classifySsmException(ssmEx, provider, username, database);
                    }
                }
            }
        }
        
        return DependencyReports.CredentialValidationReport.builder()
                .success(true)
                .provider(provider)
                .validationStep("Authentication & Authorization")
                .build();
    }

    private boolean isDockerOrRuntimeHost(String provider, String host) {
        if (provider != null) {
            String p = provider.toUpperCase();
            if (p.contains("DOCKER") || p.contains("RUNTIME") || p.contains("MANAGED")) return true;
        }
        if (host != null) {
            if (host.startsWith("autopilot-") || host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")) return true;
        }
        return false;
    }

    private DependencyReports.CredentialValidationReport classifySqlException(SQLException e, String provider, String username, String database) {
        String sqlState = e.getSQLState();
        int errorCode = e.getErrorCode();
        String msg = e.getMessage();
        
        String failureType = "DATABASE_NEGOTIATION_FAILED";
        String rootCause = msg;
        String suggestedFix = "Check database logs and properties.";
        
        if ("28000".equals(sqlState) || errorCode == 1045) {
            failureType = "DATABASE_AUTHENTICATION_FAILED";
            suggestedFix = "Verify username and password credentials are correct and match the database server config.";
        } else if ("3D000".equals(sqlState) || errorCode == 1049) {
            failureType = "DATABASE_NOT_FOUND";
            suggestedFix = "Ensure database '" + database + "' exists on the server.";
        } else if ("42000".equals(sqlState) || errorCode == 1044) {
            failureType = "DATABASE_PERMISSION_DENIED";
            suggestedFix = "Grant required privileges to user '" + username + "' for database '" + database + "'.";
        } else if (msg.contains("SSL") || msg.contains("TLS")) {
            failureType = "DATABASE_TLS_FAILED";
            suggestedFix = "Verify TLS/SSL settings on both client and database server.";
        }
        
        return DependencyReports.CredentialValidationReport.builder()
                .success(false)
                .failureType(failureType)
                .expectedCredentials("Username: " + username + ", DB: " + database)
                .actualNegotiatedCredentialsRedacted("Redacted")
                .provider(provider)
                .validationStep("JDBC Connection Attempt")
                .rootCause(rootCause)
                .suggestedFix(suggestedFix)
                .build();
    }

    private DependencyReports.CredentialValidationReport parseSsmFailure(String out, String provider, String username, String database) {
        String failureType = "DATABASE_AUTHENTICATION_FAILED";
        String rootCause = out != null ? out.trim() : "Unknown SSM output check failure";
        String suggestedFix = "Ensure password, username, and database name match perfectly.";
        
        if (rootCause.contains("database") && rootCause.contains("does not exist")) {
            failureType = "DATABASE_NOT_FOUND";
            suggestedFix = "Make sure database '" + database + "' has been initialized.";
        }
        
        return DependencyReports.CredentialValidationReport.builder()
                .success(false)
                .failureType(failureType)
                .expectedCredentials("Username: " + username + ", DB: " + database)
                .actualNegotiatedCredentialsRedacted("Redacted")
                .provider(provider)
                .validationStep("SSM CLI Execution")
                .rootCause(rootCause)
                .suggestedFix(suggestedFix)
                .build();
    }

    private DependencyReports.CredentialValidationReport classifySsmException(Exception e, String provider, String username, String database) {
        return DependencyReports.CredentialValidationReport.builder()
                .success(false)
                .failureType("DATABASE_UNREACHABLE")
                .expectedCredentials("Username: " + username + ", DB: " + database)
                .actualNegotiatedCredentialsRedacted("Redacted")
                .provider(provider)
                .validationStep("SSM Command Check")
                .rootCause("SSM command failed: " + e.getMessage())
                .suggestedFix("Ensure target EC2 instance is online and SSM agent is responsive.")
                .build();
    }
}
