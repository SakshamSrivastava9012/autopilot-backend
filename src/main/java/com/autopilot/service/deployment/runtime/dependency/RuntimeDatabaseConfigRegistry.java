package com.autopilot.service.deployment.runtime.dependency;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class RuntimeDatabaseConfigRegistry {
    private static final Map<String, RuntimeDatabaseConfiguration> cache = new ConcurrentHashMap<>();

    public static RuntimeDatabaseConfiguration getOrCreate(String dbType, String dbName) {
        String key = dbType.toLowerCase() + ":" + (dbName != null ? dbName.toLowerCase() : "autopilotdb");
        System.out.println("[TRACE] Method: getOrCreate");
        System.out.println("[TRACE] Class: com.autopilot.service.deployment.runtime.dependency.RuntimeDatabaseConfigRegistry");
        System.out.println("[TRACE] Object received: dbType=" + dbType + ", dbName=" + dbName);
        RuntimeDatabaseConfiguration dbConfig = cache.computeIfAbsent(key, k -> {
            String db = (dbName != null && !dbName.isBlank()) ? dbName : "autopilotdb";
            String username = "autopilot";
            String password = "AP" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            String rootPassword = password;
            int port = 3306;
            String containerName = "autopilot-mysql";

            String typeLower = dbType.toLowerCase();
            if ("postgres".equals(typeLower) || "postgresql".equals(typeLower)) {
                port = 5432;
                containerName = "autopilot-postgres";
            } else if ("mongo".equals(typeLower) || "mongodb".equals(typeLower)) {
                port = 27017;
                containerName = "autopilot-mongo";
            } else if ("redis".equals(typeLower)) {
                port = 6379;
                containerName = "autopilot-redis";
            }
            return new RuntimeDatabaseConfiguration(db, username, password, rootPassword, port, containerName);
        });
        System.out.println("[TRACE] Object returned: RuntimeDatabaseConfiguration [databaseName=" + dbConfig.databaseName() + ", containerName=" + dbConfig.containerName() + "]");
        System.out.println("[TRACE] RuntimeDatabaseConfiguration instance hash: " + System.identityHashCode(dbConfig));
        return dbConfig;
    }

    public static void clear() {
        cache.clear();
    }
}
