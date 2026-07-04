package com.autopilot.service.deployment.runtime.dependency;

import java.util.UUID;

public class DependencyProviderFactory {
    public static DependencyProvider create(String type) {
        return create(type, "autopilotdb");
    }

    public static DependencyProvider create(String type, String databaseName) {
        System.out.println("[TRACE] Method: create");
        System.out.println("[TRACE] Class: com.autopilot.service.deployment.runtime.dependency.DependencyProviderFactory");
        System.out.println("[TRACE] Object received: type=" + type + ", databaseName=" + databaseName);
        String dbType = type.toLowerCase();
        
        if ("mysql".equals(dbType) || "mariadb".equals(dbType)) {
            RuntimeDatabaseConfiguration config = RuntimeDatabaseConfigRegistry.getOrCreate("mysql", databaseName);
            DependencyProvider provider = new MySqlDependencyProvider(config);
            System.out.println("[TRACE] Object returned: MySqlDependencyProvider");
            System.out.println("[TRACE] RuntimeDatabaseConfiguration instance hash: " + System.identityHashCode(config));
            return provider;
        } else if ("postgres".equals(dbType) || "postgresql".equals(dbType)) {
            RuntimeDatabaseConfiguration config = RuntimeDatabaseConfigRegistry.getOrCreate("postgres", databaseName);
            DependencyProvider provider = new PostgresDependencyProvider(config);
            System.out.println("[TRACE] Object returned: PostgresDependencyProvider");
            System.out.println("[TRACE] RuntimeDatabaseConfiguration instance hash: " + System.identityHashCode(config));
            return provider;
        } else if ("mongo".equals(dbType) || "mongodb".equals(dbType)) {
            RuntimeDatabaseConfiguration config = RuntimeDatabaseConfigRegistry.getOrCreate("mongodb", databaseName);
            DependencyProvider provider = new MongoDependencyProvider(config);
            System.out.println("[TRACE] Object returned: MongoDependencyProvider");
            System.out.println("[TRACE] RuntimeDatabaseConfiguration instance hash: " + System.identityHashCode(config));
            return provider;
        } else if ("redis".equals(dbType)) {
            DependencyProvider provider = new RedisDependencyProvider();
            System.out.println("[TRACE] Object returned: RedisDependencyProvider");
            return provider;
        }
        throw new IllegalArgumentException("Unsupported dependency type: " + type);
    }
}
