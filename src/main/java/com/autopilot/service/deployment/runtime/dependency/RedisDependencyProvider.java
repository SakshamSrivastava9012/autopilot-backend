package com.autopilot.service.deployment.runtime.dependency;

import java.util.List;

public class RedisDependencyProvider implements DependencyProvider {

    @Override
    public ContainerId start() {
        return new ContainerId("autopilot-redis");
    }

    @Override
    public StartupResult waitUntilReady() {
        List<String> commands = List.of(
            "docker pull redis:7-alpine",
            "docker rm -f autopilot-redis 2>/dev/null || true",
            "docker run -d --name autopilot-redis --network autopilot --restart unless-stopped redis:7-alpine",
            "# DEBUG: Starting Startup Negotiation Engine for autopilot-redis"
        );
        return new StartupResult(true, commands, null);
    }

    @Override
    public HealthResult health() {
        return new HealthResult(true, "Redis healthy check");
    }

    @Override
    public ConnectionInfo connectionInfo() {
        String host = "autopilot-redis";
        int port = 6379;
        String uri = "redis://" + host + ":" + port;
        return new ConnectionInfo(host, port, "", "", "0", uri);
    }

    @Override
    public List<String> cleanup() {
        return List.of("docker rm -f autopilot-redis 2>/dev/null || true");
    }

    @Override
    public List<String> diagnostics() {
        return List.of(
            "docker ps -a",
            "docker inspect autopilot-redis",
            "docker logs --tail 200 autopilot-redis"
        );
    }
}
