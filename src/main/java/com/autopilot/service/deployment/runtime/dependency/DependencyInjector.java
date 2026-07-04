package com.autopilot.service.deployment.runtime.dependency;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DependencyInjector {

    public Map<String, String> generateAndSanitizePayload(
            CredentialContract contract,
            String style,
            Map<String, String> existingEnv
    ) {
        System.out.println("💉 Generating & Sanitizing Payload for: " + contract.getProvider() + " (Style: " + style + ")");
        
        Map<String, String> result = new LinkedHashMap<>(existingEnv);
        Map<String, String> injected = new HashMap<>();
        
        String provider = contract.getProvider();
        String host = contract.getHost();
        String port = String.valueOf(contract.getPort());
        String username = contract.getUsername();
        String password = contract.getPassword();
        String database = contract.getDatabase();
        String uri = contract.getUri();

        // 1. Generate framework-specific properties
        if ("SPRING_DATASOURCE".equalsIgnoreCase(style) || "SPRING_BOOT".equalsIgnoreCase(style)) {
            if ("MYSQL".equalsIgnoreCase(provider) || "MARIADB".equalsIgnoreCase(provider)) {
                RuntimeDatabaseConfiguration config = RuntimeDatabaseConfigRegistry.getOrCreate("mysql", database);
                
                // Use ApplicationRuntimeInjector (Requirement 5)
                ApplicationRuntimeInjector appInjector = new ApplicationRuntimeInjector(config);
                Map<String, String> springVars = appInjector.createSpringEnvironmentVariables("mysql");
                injected.putAll(springVars);
                
                // Use EnvironmentVariableInjector (Requirement 3)
                EnvironmentVariableInjector envInjector = new EnvironmentVariableInjector(config);
                Map<String, String> envVars = envInjector.createEnvironmentVariables("mysql");
                
                injected.put("MYSQL_DATABASE", config.databaseName());
                injected.put("MYSQL_USER", config.username());
                injected.put("MYSQL_PASSWORD", config.password());
                injected.put("MYSQL_ROOT_PASSWORD", config.rootPassword());
                
                // Validation (Requirement 6)
                if (!config.username().equals(username) || !config.password().equals(password) || !config.databaseName().equals(database)) {
                    throw new RuntimeConfigurationMismatchException(
                        "Runtime Configuration Mismatch: Spring Boot (" + username + "/" + database + ") vs MySQL DB (" + config.username() + "/" + config.databaseName() + ")"
                    );
                }
                
                // Diagnostics (Requirement 7)
                Diagnostics diagnostics = new Diagnostics(config);
                diagnostics.printDiagnostics("mysql", "DependencyInjector");
                
                // Health & Wait engines (Requirement 3)
                new HealthEngine(config).checkHealth();
                new WaitEngine(config).waitForReady();
            } else if ("POSTGRESQL".equalsIgnoreCase(provider) || "POSTGRES".equalsIgnoreCase(provider)) {
                RuntimeDatabaseConfiguration config = RuntimeDatabaseConfigRegistry.getOrCreate("postgres", database);
                
                ApplicationRuntimeInjector appInjector = new ApplicationRuntimeInjector(config);
                Map<String, String> springVars = appInjector.createSpringEnvironmentVariables("postgres");
                injected.putAll(springVars);
                
                EnvironmentVariableInjector envInjector = new EnvironmentVariableInjector(config);
                Map<String, String> envVars = envInjector.createEnvironmentVariables("postgres");
                
                if (!config.username().equals(username) || !config.password().equals(password) || !config.databaseName().equals(database)) {
                    throw new RuntimeConfigurationMismatchException(
                        "Runtime Configuration Mismatch: Spring Boot (" + username + "/" + database + ") vs Postgres DB (" + config.username() + "/" + config.databaseName() + ")"
                    );
                }
                
                Diagnostics diagnostics = new Diagnostics(config);
                diagnostics.printDiagnostics("postgres", "DependencyInjector");
                
                new HealthEngine(config).checkHealth();
                new WaitEngine(config).waitForReady();
            } else if ("MONGO".equalsIgnoreCase(provider) || "MONGODB".equalsIgnoreCase(provider)) {
                RuntimeDatabaseConfiguration config = RuntimeDatabaseConfigRegistry.getOrCreate("mongodb", database);
                
                ApplicationRuntimeInjector appInjector = new ApplicationRuntimeInjector(config);
                Map<String, String> springVars = appInjector.createSpringEnvironmentVariables("mongo");
                injected.put("SPRING_DATA_MONGODB_URI", springVars.get("SPRING_DATASOURCE_URL"));
                
                EnvironmentVariableInjector envInjector = new EnvironmentVariableInjector(config);
                Map<String, String> envVars = envInjector.createEnvironmentVariables("mongo");
                
                if (!config.username().equals(username) || !config.password().equals(password) || !config.databaseName().equals(database)) {
                    throw new RuntimeConfigurationMismatchException(
                        "Runtime Configuration Mismatch: Spring Boot (" + username + "/" + database + ") vs Mongo DB (" + config.username() + "/" + config.databaseName() + ")"
                    );
                }
                
                Diagnostics diagnostics = new Diagnostics(config);
                diagnostics.printDiagnostics("mongodb", "DependencyInjector");
                
                new HealthEngine(config).checkHealth();
                new WaitEngine(config).waitForReady();
            } else if ("REDIS".equalsIgnoreCase(provider)) {
                injected.put("SPRING_DATA_REDIS_HOST", host);
                injected.put("SPRING_DATA_REDIS_PORT", port);
            }
        } else if ("QUARKUS".equalsIgnoreCase(style)) {
            if ("MYSQL".equalsIgnoreCase(provider) || "POSTGRESQL".equalsIgnoreCase(provider)) {
                injected.put("QUARKUS_DATASOURCE_JDBC_URL", uri);
                injected.put("QUARKUS_DATASOURCE_USERNAME", username);
                injected.put("QUARKUS_DATASOURCE_PASSWORD", password);
            }
        } else if ("MICRONAUT".equalsIgnoreCase(style)) {
            if ("MYSQL".equalsIgnoreCase(provider) || "POSTGRESQL".equalsIgnoreCase(provider)) {
                injected.put("DATASOURCES_DEFAULT_URL", uri);
                injected.put("DATASOURCES_DEFAULT_USERNAME", username);
                injected.put("DATASOURCES_DEFAULT_PASSWORD", password);
            }
        } else if ("LARAVEL".equalsIgnoreCase(style)) {
            injected.put("DB_CONNECTION", provider.toLowerCase());
            injected.put("DB_HOST", host);
            injected.put("DB_PORT", port);
            injected.put("DB_DATABASE", database);
            injected.put("DB_USERNAME", username);
            injected.put("DB_PASSWORD", password);
        } else {
            // Default Node / Prisma / Django / FastAPI / Go / Rails
            injected.put("DATABASE_URL", uri);
            if ("REDIS".equalsIgnoreCase(provider)) {
                injected.put("REDIS_URL", uri);
            } else if ("MONGO".equalsIgnoreCase(provider)) {
                injected.put("MONGODB_URI", uri);
            }
        }

        // Put new variables
        result.putAll(injected);

        // 2. Sanitization: remove conflicting / duplicate / wrong provider variables
        Set<String> keysToRemove = new HashSet<>();
        
        if ("SPRING_DATASOURCE".equalsIgnoreCase(style) || "SPRING_BOOT".equalsIgnoreCase(style)) {
            // Remove non-spring database vars
            keysToRemove.addAll(Arrays.asList(
                "DATABASE_URL", "DB_HOST", "DB_PORT", "DB_USER", "DB_PASSWORD", "DB_NAME",
                "DB_DATABASE", "DB_USERNAME", "QUARKUS_DATASOURCE_JDBC_URL", "QUARKUS_DATASOURCE_USERNAME",
                "QUARKUS_DATASOURCE_PASSWORD", "DATASOURCES_DEFAULT_URL"
            ));
        } else if ("DATABASE_URL_ONLY".equalsIgnoreCase(style) || "NODE".equalsIgnoreCase(style) || "GENERIC_ENV".equalsIgnoreCase(style)) {
            // Remove Spring / Quarkus / Laravel specific database vars
            keysToRemove.addAll(Arrays.asList(
                "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
                "SPRING_DATA_MONGODB_URI", "SPRING_DATA_REDIS_HOST", "SPRING_DATA_REDIS_PORT",
                "QUARKUS_DATASOURCE_JDBC_URL", "QUARKUS_DATASOURCE_USERNAME", "QUARKUS_DATASOURCE_PASSWORD",
                "DATASOURCES_DEFAULT_URL", "DB_DATABASE", "DB_USERNAME"
            ));
        } else if ("LARAVEL".equalsIgnoreCase(style)) {
            // Keep Laravel only
            keysToRemove.addAll(Arrays.asList(
                "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
                "SPRING_DATA_MONGODB_URI", "SPRING_DATA_REDIS_HOST", "SPRING_DATA_REDIS_PORT",
                "DATABASE_URL", "QUARKUS_DATASOURCE_JDBC_URL", "QUARKUS_DATASOURCE_USERNAME",
                "QUARKUS_DATASOURCE_PASSWORD", "DATASOURCES_DEFAULT_URL"
            ));
        }

        // Always remove placeholders
        for (Map.Entry<String, String> entry : result.entrySet()) {
            if (entry.getValue() != null && (entry.getValue().contains("placeholder") || entry.getValue().contains("<placeholder>"))) {
                keysToRemove.add(entry.getKey());
            }
        }

        for (String key : keysToRemove) {
            result.remove(key);
        }

        return result;
    }

    public Map<String, String> generateInjectionPayload(DependencyDescriptor descriptor) {
        // Fallback backward-compatible method
        Map<String, String> envVars = new HashMap<>();
        if ("PostgreSQL".equalsIgnoreCase(descriptor.getType())) {
            envVars.put("DATABASE_URL", "jdbc:postgresql://postgres-runtime:5432/db");
            envVars.put("POSTGRES_USER", "autopilot");
        } else if ("MongoDB".equalsIgnoreCase(descriptor.getType())) {
            envVars.put("MONGO_URI", "mongodb://mongo-runtime:27017/db");
        }
        return envVars;
    }
}
