package com.autopilot.service.deployment.runtime.dependency;

public class Diagnostics {
    private final RuntimeDatabaseConfiguration config;
    private final RuntimeContainerDescriptor descriptor;

    public Diagnostics(RuntimeDatabaseConfiguration config) {
        this.config = config;
        this.descriptor = null;
    }

    public Diagnostics(RuntimeContainerDescriptor descriptor) {
        this.config = null;
        this.descriptor = descriptor;
    }

    public void printDiagnostics(String dbType, String configSource) {
        String host = (descriptor != null) ? descriptor.databaseContainerName() : (config != null ? config.containerName() : "unknown");
        int port = (descriptor != null && descriptor.databasePort() != null) ? descriptor.databasePort() : (config != null ? config.port() : 0);
        String dbName = config != null ? config.databaseName() : "unknown";
        String username = config != null ? config.username() : "unknown";
        String password = config != null ? config.password() : "unknown";
        String passwordHash = password != null ? Integer.toHexString(password.hashCode()) : "0";

        String url = "";
        String typeLower = dbType != null ? dbType.toLowerCase() : "";
        if ("mysql".equals(typeLower) || "mariadb".equals(typeLower)) {
            String mysqlJdbcParams = "?allowPublicKeyRetrieval=true&useSSL=false&createDatabaseIfNotExist=true";
            url = "jdbc:mysql://" + host + ":" + port + "/" + dbName + mysqlJdbcParams;
        } else if ("postgres".equals(typeLower) || "postgresql".equals(typeLower)) {
            url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        } else if ("mongo".equals(typeLower) || "mongodb".equals(typeLower)) {
            url = "mongodb://" + username + ":" + password + "@" + host + ":" + port + "/" + dbName + "?authSource=admin";
        }

        String maskedPassword = (password != null && password.length() > 4) ? password.substring(0, 3) + "***" : "***";
        
        System.out.println("==================================================");
        System.out.println("📊 DATABASE RUNTIME CONFIGURATION DIAGNOSTICS");
        System.out.println("==================================================");
        System.out.println("Configuration Source: " + configSource);
        System.out.println("Database Name:        " + dbName);
        System.out.println("Database User:        " + username);
        System.out.println("Datasource URL:       " + url);
        System.out.println("Container Name:       " + host);
        System.out.println("Network:              " + (descriptor != null ? descriptor.networkName() : "autopilot"));
        System.out.println("Password Hash (Hex):  " + passwordHash);
        System.out.println("Masked Password:      " + maskedPassword);
        System.out.println("Proof of Instance:    " + (config != null ? System.identityHashCode(config) : "N/A"));
        System.out.println("==================================================");
    }
}
