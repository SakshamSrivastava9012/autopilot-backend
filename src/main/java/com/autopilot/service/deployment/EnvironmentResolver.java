package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.dto.DeploymentService;
import org.springframework.stereotype.Service;
import com.autopilot.service.deployment.runtime.dependency.*;
import java.util.*;

@Service
public class EnvironmentResolver {

    private final com.autopilot.service.deployment.runtime.configuration.ConfigurationNegotiationEngine configEngine;

    public EnvironmentResolver(com.autopilot.service.deployment.runtime.configuration.ConfigurationNegotiationEngine configEngine) {
        this.configEngine = configEngine;
    }

    public Map<String, String> resolveEnvironmentVariables(
            ServiceConfig svc,
            String publicIp,
            String basePath,
            String rdsEndpoint,
            String redisEndpoint
    ) {
        Map<String, String> env = new HashMap<>();

        env.put("PORT", String.valueOf(svc.getPort() != null ? svc.getPort() : 8080));
        env.put("SERVER_PORT", String.valueOf(svc.getPort() != null ? svc.getPort() : 8080));

        String svcBasePath = svc.getBasePath() != null ? svc.getBasePath() : basePath;
        env.put("BASE_PATH", svcBasePath);
        env.put("PUBLIC_BASE_PATH", basePath);
        env.put("API_BASE_PATH", basePath + "-api");

        String externalUrl = "http://" + publicIp + svcBasePath;
        if (!externalUrl.endsWith("/")) {
            externalUrl += "/";
        }
        env.put("EXTERNAL_URL", externalUrl);
        env.put("PUBLIC_URL", "http://" + publicIp + basePath + "/");
        env.put("API_URL", "http://" + publicIp + basePath + "-api/");

        boolean isSpring = false;
        if (svc.getFramework() != null) {
            String fw = svc.getFramework().toLowerCase();
            if (fw.contains("spring") || fw.contains("quarkus")) {
                isSpring = true;
            }
        }
        if (svc.getLanguage() != null) {
            String lang = svc.getLanguage().toLowerCase();
            if (lang.equals("java") || lang.equals("kotlin")) {
                isSpring = true;
            }
        }

        if (rdsEndpoint != null && !rdsEndpoint.isBlank()) {
            String dbType = "mysql";
            if (rdsEndpoint.contains("postgres") || rdsEndpoint.contains("5432")) {
                dbType = "postgres";
            } else if (rdsEndpoint.contains("mongo") || rdsEndpoint.contains("27017")) {
                dbType = "mongodb";
            }

            RuntimeDatabaseConfiguration dbConfig = RuntimeDatabaseConfigRegistry.getOrCreate(dbType, "autopilotdb");

            System.out.println("[TRACE] Method: resolveEnvironmentVariables");
            System.out.println("[TRACE] Class: com.autopilot.service.deployment.EnvironmentResolver");
            System.out.println("[TRACE] Object received: rdsEndpoint=" + rdsEndpoint + ", isSpring=" + isSpring);
            System.out.println("[TRACE] RuntimeDatabaseConfiguration instance hash: " + System.identityHashCode(dbConfig));

            if (isSpring) {
                ApplicationRuntimeInjector appInjector = new ApplicationRuntimeInjector(dbConfig);
                Map<String, String> springVars = appInjector.createSpringEnvironmentVariables(dbType);
                env.putAll(springVars);
                System.out.println("[TRACE] Object returned: Map of size " + springVars.size());
                System.out.println("[TRACE] Datasource variables generated: " + springVars);
            } else {
                EnvironmentVariableInjector envInjector = new EnvironmentVariableInjector(dbConfig);
                Map<String, String> envVars = envInjector.createEnvironmentVariables(dbType);
                env.putAll(envVars);
                env.put("DB_HOST", rdsEndpoint.contains(":") ? rdsEndpoint.split(":")[0] : rdsEndpoint);
                env.put("DB_PORT", rdsEndpoint.contains(":") ? rdsEndpoint.split(":")[1] : (dbType.equals("postgres") ? "5432" : "3306"));
                env.put("DB_NAME", dbConfig.databaseName());
                env.put("DB_USER", dbConfig.username());
                env.put("DB_PASSWORD", dbConfig.password());
                System.out.println("[TRACE] Object returned: Map of size " + envVars.size());
                System.out.println("[TRACE] Datasource variables generated: " + envVars);
            }
        }

        if (redisEndpoint != null && !redisEndpoint.isBlank()) {
            if (isSpring) {
                env.put("SPRING_REDIS_HOST", redisEndpoint);
            } else {
                env.put("REDIS_HOST", redisEndpoint);
            }
        }

        env.put("OAUTH2_CALLBACK_URL", externalUrl + "login/oauth2/code/");
        env.put("X_FORWARDED_PREFIX", svcBasePath);
        env.put("X_FORWARDED_HOST", publicIp);
        env.put("X_FORWARDED_PROTO", "http");

        if (configEngine != null) {
            Map<String, String> metadata = new HashMap<>();
            if (isSpring) {
                metadata.put("SPRING_BOOT", "true");
            }
            configEngine.negotiateEnvironment(metadata, env);
        }

        return env;
    }

    public Map<String, String> resolveEnvironmentVariables(
            DeploymentService svc,
            String publicIp,
            String basePath,
            String rdsEndpoint,
            String redisEndpoint
    ) {
        Map<String, String> env = new HashMap<>();

        env.put("PORT", String.valueOf(svc.getPort() > 0 ? svc.getPort() : 8080));
        env.put("SERVER_PORT", String.valueOf(svc.getPort() > 0 ? svc.getPort() : 8080));

        String svcBasePath = svc.getBasePath() != null ? svc.getBasePath() : basePath;
        env.put("BASE_PATH", svcBasePath);
        env.put("PUBLIC_BASE_PATH", basePath);
        env.put("API_BASE_PATH", basePath + "-api");

        String externalUrl = "http://" + publicIp + svcBasePath;
        if (!externalUrl.endsWith("/")) {
            externalUrl += "/";
        }
        env.put("EXTERNAL_URL", externalUrl);
        env.put("PUBLIC_URL", "http://" + publicIp + basePath + "/");
        env.put("API_URL", "http://" + publicIp + basePath + "-api/");

        boolean isSpring = false;
        if (svc.getFramework() != null) {
            String fw = svc.getFramework().toLowerCase();
            if (fw.contains("spring") || fw.contains("quarkus")) {
                isSpring = true;
            }
        }
        if (svc.getLanguage() != null) {
            String lang = svc.getLanguage().toLowerCase();
            if (lang.equals("java") || lang.equals("kotlin")) {
                isSpring = true;
            }
        }

        if (rdsEndpoint != null && !rdsEndpoint.isBlank()) {
            String dbType = "mysql";
            if (rdsEndpoint.contains("postgres") || rdsEndpoint.contains("5432")) {
                dbType = "postgres";
            } else if (rdsEndpoint.contains("mongo") || rdsEndpoint.contains("27017")) {
                dbType = "mongodb";
            }

            RuntimeDatabaseConfiguration dbConfig = RuntimeDatabaseConfigRegistry.getOrCreate(dbType, "autopilotdb");

            System.out.println("[TRACE] Method: resolveEnvironmentVariables");
            System.out.println("[TRACE] Class: com.autopilot.service.deployment.EnvironmentResolver");
            System.out.println("[TRACE] Object received: rdsEndpoint=" + rdsEndpoint + ", isSpring=" + isSpring);
            System.out.println("[TRACE] RuntimeDatabaseConfiguration instance hash: " + System.identityHashCode(dbConfig));

            if (isSpring) {
                ApplicationRuntimeInjector appInjector = new ApplicationRuntimeInjector(dbConfig);
                Map<String, String> springVars = appInjector.createSpringEnvironmentVariables(dbType);
                env.putAll(springVars);
                System.out.println("[TRACE] Object returned: Map of size " + springVars.size());
                System.out.println("[TRACE] Datasource variables generated: " + springVars);
            } else {
                EnvironmentVariableInjector envInjector = new EnvironmentVariableInjector(dbConfig);
                Map<String, String> envVars = envInjector.createEnvironmentVariables(dbType);
                env.putAll(envVars);
                env.put("DB_HOST", rdsEndpoint.contains(":") ? rdsEndpoint.split(":")[0] : rdsEndpoint);
                env.put("DB_PORT", rdsEndpoint.contains(":") ? rdsEndpoint.split(":")[1] : (dbType.equals("postgres") ? "5432" : "3306"));
                env.put("DB_NAME", dbConfig.databaseName());
                env.put("DB_USER", dbConfig.username());
                env.put("DB_PASSWORD", dbConfig.password());
                System.out.println("[TRACE] Object returned: Map of size " + envVars.size());
                System.out.println("[TRACE] Datasource variables generated: " + envVars);
            }
        }

        if (redisEndpoint != null && !redisEndpoint.isBlank()) {
            if (isSpring) {
                env.put("SPRING_REDIS_HOST", redisEndpoint);
            } else {
                env.put("REDIS_HOST", redisEndpoint);
            }
        }

        env.put("OAUTH2_CALLBACK_URL", externalUrl + "login/oauth2/code/");
        env.put("X_FORWARDED_PREFIX", svcBasePath);
        env.put("X_FORWARDED_HOST", publicIp);
        env.put("X_FORWARDED_PROTO", "http");

        if (configEngine != null) {
            Map<String, String> metadata = new HashMap<>();
            if (isSpring) {
                metadata.put("SPRING_BOOT", "true");
            }
            configEngine.negotiateEnvironment(metadata, env);
        }

        return env;
    }
}
