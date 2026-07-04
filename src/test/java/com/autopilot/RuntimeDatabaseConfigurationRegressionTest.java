package com.autopilot;

import com.autopilot.service.deployment.runtime.dependency.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Deployrix V5.11 — Runtime Configuration Pipeline Regression Test")
public class RuntimeDatabaseConfigurationRegressionTest {

    @BeforeEach
    void setUp() {
        RuntimeDatabaseConfigRegistry.clear();
    }

    @Test
    @DisplayName("Spring Boot + MySQL consumes the same RuntimeDatabaseConfiguration object and validation succeeds when matching")
    void testSpringBootAndMySqlMatching() {
        // 1. Get database configuration from central registry
        RuntimeDatabaseConfiguration dbConfig = RuntimeDatabaseConfigRegistry.getOrCreate("mysql", "autopilotdb");
        assertNotNull(dbConfig);

        // 2. Instantiate MySqlDependencyProvider
        MySqlDependencyProvider provider = new MySqlDependencyProvider(dbConfig);
        assertEquals("autopilot-mysql", provider.start().value());
        
        StartupResult startupResult = provider.waitUntilReady();
        // Check thatMYSQL environment variables are created using ONLY RuntimeDatabaseConfiguration
        assertTrue(startupResult.commands().stream().anyMatch(c -> c.contains("MYSQL_USER=" + dbConfig.username())));
        assertTrue(startupResult.commands().stream().anyMatch(c -> c.contains("MYSQL_PASSWORD=" + dbConfig.password())));
        assertTrue(startupResult.commands().stream().anyMatch(c -> c.contains("MYSQL_DATABASE=" + dbConfig.databaseName())));
        assertTrue(startupResult.commands().stream().anyMatch(c -> c.contains("MYSQL_ROOT_PASSWORD=" + dbConfig.rootPassword())));

        // 3. Inject matching environment variables into Spring Boot config
        DependencyInjector injector = new DependencyInjector();
        CredentialContract contract = CredentialContract.builder()
                .provider("MYSQL")
                .host(dbConfig.containerName())
                .port(dbConfig.port())
                .username(dbConfig.username())
                .password(dbConfig.password())
                .database(dbConfig.databaseName())
                .uri(provider.connectionInfo().uri())
                .build();

        Map<String, String> rawEnv = Map.of(
                "SPRING_BOOT", "true",
                "SPRING_DATASOURCE_URL", provider.connectionInfo().uri(),
                "SPRING_DATASOURCE_USERNAME", dbConfig.username(),
                "SPRING_DATASOURCE_PASSWORD", dbConfig.password()
        );

        // This must run successfully without throwing RuntimeConfigurationMismatchException
        Map<String, String> injectedEnv = injector.generateAndSanitizePayload(contract, "SPRING_BOOT", rawEnv);
        
        // Assert Spring Boot properties match the DB configuration
        assertEquals(dbConfig.username(), injectedEnv.get("SPRING_DATASOURCE_USERNAME"));
        assertEquals(dbConfig.password(), injectedEnv.get("SPRING_DATASOURCE_PASSWORD"));
        assertEquals(dbConfig.databaseName(), injectedEnv.get("MYSQL_DATABASE"));
    }

    @Test
    @DisplayName("Spring Boot + MySQL throws RuntimeConfigurationMismatchException when credentials/databases mismatch")
    void testSpringBootAndMySqlMismatch() {
        RuntimeDatabaseConfiguration dbConfig = RuntimeDatabaseConfigRegistry.getOrCreate("mysql", "autopilotdb");

        DependencyInjector injector = new DependencyInjector();
        CredentialContract contract = CredentialContract.builder()
                .provider("MYSQL")
                .host(dbConfig.containerName())
                .port(dbConfig.port())
                .username("wrong-username") // Mismatched!
                .password(dbConfig.password())
                .database(dbConfig.databaseName())
                .uri("jdbc:mysql://" + dbConfig.containerName() + ":3306/" + dbConfig.databaseName())
                .build();

        Map<String, String> rawEnv = Map.of(
                "SPRING_BOOT", "true",
                "SPRING_DATASOURCE_URL", "jdbc:mysql://" + dbConfig.containerName() + ":3306/" + dbConfig.databaseName(),
                "SPRING_DATASOURCE_USERNAME", "wrong-username",
                "SPRING_DATASOURCE_PASSWORD", dbConfig.password()
        );

        assertThrows(RuntimeConfigurationMismatchException.class, () -> {
            injector.generateAndSanitizePayload(contract, "SPRING_BOOT", rawEnv);
        });
    }

    @Test
    @DisplayName("Spring Boot + PostgreSQL consumes the same RuntimeDatabaseConfiguration object and validation succeeds when matching")
    void testSpringBootAndPostgreSqlMatching() {
        RuntimeDatabaseConfiguration dbConfig = RuntimeDatabaseConfigRegistry.getOrCreate("postgres", "autopilotdb");
        assertNotNull(dbConfig);

        PostgresDependencyProvider provider = new PostgresDependencyProvider(dbConfig);
        assertEquals("autopilot-postgres", provider.start().value());

        StartupResult startupResult = provider.waitUntilReady();
        assertTrue(startupResult.commands().stream().anyMatch(c -> c.contains("POSTGRES_USER=" + dbConfig.username())));
        assertTrue(startupResult.commands().stream().anyMatch(c -> c.contains("POSTGRES_PASSWORD=" + dbConfig.password())));
        assertTrue(startupResult.commands().stream().anyMatch(c -> c.contains("POSTGRES_DB=" + dbConfig.databaseName())));

        DependencyInjector injector = new DependencyInjector();
        CredentialContract contract = CredentialContract.builder()
                .provider("POSTGRESQL")
                .host(dbConfig.containerName())
                .port(dbConfig.port())
                .username(dbConfig.username())
                .password(dbConfig.password())
                .database(dbConfig.databaseName())
                .uri(provider.connectionInfo().uri())
                .build();

        Map<String, String> rawEnv = Map.of(
                "SPRING_BOOT", "true",
                "SPRING_DATASOURCE_URL", provider.connectionInfo().uri(),
                "SPRING_DATASOURCE_USERNAME", dbConfig.username(),
                "SPRING_DATASOURCE_PASSWORD", dbConfig.password()
        );

        Map<String, String> injectedEnv = injector.generateAndSanitizePayload(contract, "SPRING_BOOT", rawEnv);
        assertEquals(dbConfig.username(), injectedEnv.get("SPRING_DATASOURCE_USERNAME"));
        assertEquals(dbConfig.password(), injectedEnv.get("SPRING_DATASOURCE_PASSWORD"));
    }

    @Test
    @DisplayName("Spring Boot + MongoDB consumes the same RuntimeDatabaseConfiguration object and validation succeeds when matching")
    void testSpringBootAndMongoMatching() {
        RuntimeDatabaseConfiguration dbConfig = RuntimeDatabaseConfigRegistry.getOrCreate("mongodb", "autopilotdb");
        assertNotNull(dbConfig);

        MongoDependencyProvider provider = new MongoDependencyProvider(dbConfig);
        assertEquals("autopilot-mongo", provider.start().value());

        StartupResult startupResult = provider.waitUntilReady();
        assertTrue(startupResult.commands().stream().anyMatch(c -> c.contains("MONGO_INITDB_ROOT_USERNAME=" + dbConfig.username())));
        assertTrue(startupResult.commands().stream().anyMatch(c -> c.contains("MONGO_INITDB_ROOT_PASSWORD=" + dbConfig.password())));

        DependencyInjector injector = new DependencyInjector();
        CredentialContract contract = CredentialContract.builder()
                .provider("MONGODB")
                .host(dbConfig.containerName())
                .port(dbConfig.port())
                .username(dbConfig.username())
                .password(dbConfig.password())
                .database(dbConfig.databaseName())
                .uri(provider.connectionInfo().uri())
                .build();

        Map<String, String> rawEnv = Map.of(
                "SPRING_BOOT", "true",
                "SPRING_DATASOURCE_URL", provider.connectionInfo().uri(),
                "SPRING_DATASOURCE_USERNAME", dbConfig.username(),
                "SPRING_DATASOURCE_PASSWORD", dbConfig.password()
        );

        Map<String, String> injectedEnv = injector.generateAndSanitizePayload(contract, "SPRING_BOOT", rawEnv);
        assertNotNull(injectedEnv.get("SPRING_DATA_MONGODB_URI"));
        assertTrue(injectedEnv.get("SPRING_DATA_MONGODB_URI").contains(dbConfig.password()));
    }
}
