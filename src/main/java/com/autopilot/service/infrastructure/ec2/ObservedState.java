package com.autopilot.service.infrastructure.ec2;

import lombok.Data;

@Data
public class ObservedState {
    private String containerName;
    private boolean inspectSuccess;
    private boolean running;
    private boolean restarting;
    private boolean dead;
    private int exitCode = -1;
    private String startedAt;
    private String finishedAt;
    private boolean oomKilled;
    private String dockerHealthStatus = "none"; // "healthy", "unhealthy", "starting", "none"
    private int pid;
    private int restartCount;
    private boolean internalPortBound;
    private boolean hostPortBound;
    private String logs = "";
    private int healthHttpCode = -1;
    private boolean healthHttpSuccess;
    private boolean tcpConnected;
}
