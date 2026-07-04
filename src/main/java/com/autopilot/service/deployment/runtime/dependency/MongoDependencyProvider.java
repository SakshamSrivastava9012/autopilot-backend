package com.autopilot.service.deployment.runtime.dependency;

import java.util.List;

public class MongoDependencyProvider implements DependencyProvider {

    private final RuntimeDatabaseConfiguration config;

    public MongoDependencyProvider(RuntimeDatabaseConfiguration config) {
        this.config = config;
        System.out.println("[TRACE] Method: MongoDependencyProvider (Constructor)");
        System.out.println("[TRACE] Class: com.autopilot.service.deployment.runtime.dependency.MongoDependencyProvider");
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
            "docker pull mongo:6",
            "docker rm -f " + config.containerName() + " 2>/dev/null || true",
            "docker run -d --name " + config.containerName() + " --network autopilot --restart unless-stopped " +
            "-e MONGO_INITDB_ROOT_USERNAME=" + config.username() + " -e MONGO_INITDB_ROOT_PASSWORD=" + config.password() + " mongo:6",
            "# DEBUG: Starting Startup Negotiation Engine for " + config.containerName()
        );
        return new StartupResult(true, commands, null);
    }

    @Override
    public HealthResult health() {
        return new HealthResult(true, "MongoDB healthy check");
    }

    @Override
    public ConnectionInfo connectionInfo() {
        System.out.println("[TRACE] Method: connectionInfo");
        System.out.println("[TRACE] Class: com.autopilot.service.deployment.runtime.dependency.MongoDependencyProvider");
        System.out.println("[TRACE] RuntimeDatabaseConfiguration instance hash: " + System.identityHashCode(config));
        String host = config.containerName();
        int port = config.port();
        String uri = "mongodb://" + config.username() + ":" + config.password() + "@" + host + ":" + port + "/" + config.databaseName() + "?authSource=admin";
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
