package com.autopilot.service.deployment.runtime.dependency;

public record ConnectionInfo(
    String host,
    int port,
    String username,
    String password,
    String database,
    String uri
) {
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getDatabase() { return database; }
    public String getUri() { return uri; }
}
