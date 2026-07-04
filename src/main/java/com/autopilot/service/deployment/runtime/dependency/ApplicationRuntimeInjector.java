package com.autopilot.service.deployment.runtime.dependency;

import java.util.HashMap;
import java.util.Map;

public class ApplicationRuntimeInjector {
    private final RuntimeDatabaseConfiguration config;

    public ApplicationRuntimeInjector(RuntimeDatabaseConfiguration config) {
        this.config = config;
    }

    public Map<String, String> createSpringEnvironmentVariables(String dbType) {
        System.out.println("[TRACE] Method: createSpringEnvironmentVariables");
        System.out.println("[TRACE] Class: com.autopilot.service.deployment.runtime.dependency.ApplicationRuntimeInjector");
        System.out.println("[TRACE] Object received: dbType=" + dbType);
        System.out.println("[TRACE] RuntimeDatabaseConfiguration instance hash: " + System.identityHashCode(config));

        Map<String, String> env = new HashMap<>();
        String host = config.containerName();
        int port = config.port();
        String dbName = config.databaseName();
        String username = config.username();
        String password = config.password();

        String url = "";
        String typeLower = dbType.toLowerCase();
        if ("mysql".equals(typeLower) || "mariadb".equals(typeLower)) {
            String mysqlJdbcParams = "?allowPublicKeyRetrieval=true&useSSL=false&createDatabaseIfNotExist=true";
            url = "jdbc:mysql://" + host + ":" + port + "/" + dbName + mysqlJdbcParams;
        } else if ("postgres".equals(typeLower) || "postgresql".equals(typeLower)) {
            url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        } else if ("mongo".equals(typeLower) || "mongodb".equals(typeLower)) {
            url = "mongodb://" + username + ":" + password + "@" + host + ":" + port + "/" + dbName + "?authSource=admin";
        }

        env.put("SPRING_DATASOURCE_URL", url);
        env.put("SPRING_DATASOURCE_USERNAME", username);
        env.put("SPRING_DATASOURCE_PASSWORD", password);

        System.out.println("[TRACE] Object returned: Map of size " + env.size());
        System.out.println("[TRACE] Datasource variables generated: " + env);
        return env;
    }
}
