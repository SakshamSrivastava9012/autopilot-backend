package com.autopilot.service.deployment.runtime.dependency;

import java.util.List;

public class MySqlDependencyProvider implements DependencyProvider {

    private final RuntimeDatabaseConfiguration config;

    public MySqlDependencyProvider(RuntimeDatabaseConfiguration config) {
        this.config = config;
        System.out.println("[TRACE] Method: MySqlDependencyProvider (Constructor)");
        System.out.println("[TRACE] Class: com.autopilot.service.deployment.runtime.dependency.MySqlDependencyProvider");
        System.out.println("[TRACE] Object received: config=" + config);
        System.out.println("[TRACE] RuntimeDatabaseConfiguration instance hash: " + System.identityHashCode(config));
    }

    @Override
    public ContainerId start() {
        return new ContainerId(config.containerName());
    }

    @Override
    public StartupResult waitUntilReady() {
        List<String> commands = List.of(
            "docker pull mysql:8",
            "docker rm -f " + config.containerName() + " 2>/dev/null || true",
            "docker run -d --name " + config.containerName() + " --network autopilot --restart unless-stopped " +
            "-e MYSQL_USER=" + config.username() + " -e MYSQL_PASSWORD=" + config.password() +
            " -e MYSQL_DATABASE=" + config.databaseName() + " -e MYSQL_ROOT_PASSWORD=" + config.rootPassword() + " mysql:8",
            "# DEBUG: Starting Startup Negotiation Engine for " + config.containerName()
        );
        return new StartupResult(true, commands, null);
    }

    @Override
    public HealthResult health() {
        return new HealthResult(true, "MySQL healthy check");
    }

    @Override
    public ConnectionInfo connectionInfo() {
        System.out.println("[TRACE] Method: connectionInfo");
        System.out.println("[TRACE] Class: com.autopilot.service.deployment.runtime.dependency.MySqlDependencyProvider");
        System.out.println("[TRACE] RuntimeDatabaseConfiguration instance hash: " + System.identityHashCode(config));
        String host = config.containerName();
        int port = config.port();
        String mysqlJdbcParams = "?allowPublicKeyRetrieval=true&useSSL=false&createDatabaseIfNotExist=true";
        String uri = "jdbc:mysql://" + host + ":" + port + "/" + config.databaseName() + mysqlJdbcParams;
        ConnectionInfo info = new ConnectionInfo(host, port, config.username(), config.password(), config.databaseName(), uri);
        System.out.println("[TRACE] Object returned: ConnectionInfo [host=" + info.host() + ", database=" + info.database() + "]");
        return info;
    }

    @Override
    public List<String> cleanup() {
        return List.of("docker rm -f " + config.containerName() + " 2>/dev/null || true");
    }

    @Override
    public List<String> diagnostics() {
        return List.of(
            "docker ps -a",
            "docker inspect " + config.containerName(),
            "docker logs --tail 200 " + config.containerName()
        );
    }
}
