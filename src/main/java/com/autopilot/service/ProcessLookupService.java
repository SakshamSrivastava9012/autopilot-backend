package com.autopilot.service;

import java.util.List;

public interface ProcessLookupService {
    List<ProcessInfo> getRunningBackendProcesses();

    class ProcessInfo {
        private final String pid;
        private final String commandLine;

        public ProcessInfo(String pid, String commandLine) {
            this.pid = pid;
            this.commandLine = commandLine;
        }

        public String getPid() { return pid; }
        public String getCommandLine() { return commandLine; }
    }
}
