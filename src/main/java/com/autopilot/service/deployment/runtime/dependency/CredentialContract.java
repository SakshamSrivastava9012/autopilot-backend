package com.autopilot.service.deployment.runtime.dependency;

import lombok.Builder;
import lombok.Data;
import java.net.URI;

@Data
@Builder
public class CredentialContract {
    private String provider; // e.g. MYSQL, POSTGRESQL, MONGO, REDIS, KAFKA, RABBITMQ, ELASTICSEARCH
    private String host;
    private int port;
    private String username;
    private String password;
    private String database;
    private boolean ssl;
    private String uri;
    private String ownership; // PLATFORM_MANAGED, EXISTING_EXTERNAL, DOCKER_RUNTIME, USER_MANAGED
    private String generatedBy;
    private long expiresAt;

    public static CredentialContract parseUri(String connectionUri, String provider) {
        if (connectionUri == null) {
            return CredentialContract.builder()
                    .provider(provider.toUpperCase())
                    .host("localhost")
                    .port(8080)
                    .ownership("EXISTING_EXTERNAL")
                    .build();
        }

        String host = "localhost";
        int port = 3306;
        String username = "root";
        String password = "";
        String database = "autopilotdb";
        boolean ssl = false;
        
        try {
            String cleanUri = connectionUri;
            if (cleanUri.startsWith("jdbc:")) {
                cleanUri = cleanUri.substring(5);
            }
            // Strip scheme queries if URI cannot parse with query parameters directly
            URI parsed = new URI(cleanUri);
            host = parsed.getHost();
            if (parsed.getPort() != -1) {
                port = parsed.getPort();
            } else {
                if (provider.equalsIgnoreCase("MYSQL")) port = 3306;
                else if (provider.equalsIgnoreCase("POSTGRESQL") || provider.equalsIgnoreCase("POSTGRES")) port = 5432;
                else if (provider.equalsIgnoreCase("MONGO") || provider.equalsIgnoreCase("MONGODB")) port = 27017;
                else if (provider.equalsIgnoreCase("REDIS")) port = 6379;
            }
            
            String userInfo = parsed.getUserInfo();
            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts[1];
            } else if (userInfo != null) {
                username = userInfo;
            }
            
            String path = parsed.getPath();
            if (path != null && path.startsWith("/")) {
                database = path.substring(1);
                if (database.contains("?")) {
                    database = database.split("\\?")[0];
                }
            }
            if (connectionUri.contains("ssl=true") || connectionUri.contains("useSSL=true")) {
                ssl = true;
            }
        } catch (Exception ignored) {}
        
        return CredentialContract.builder()
                .provider(provider.toUpperCase())
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .database(database)
                .ssl(ssl)
                .uri(connectionUri)
                .ownership("EXISTING_EXTERNAL")
                .build();
    }
}
