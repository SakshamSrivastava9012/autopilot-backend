package com.autopilot.service.deployment.validation;

public class BuildCommand {
    private final String command;

    public BuildCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
