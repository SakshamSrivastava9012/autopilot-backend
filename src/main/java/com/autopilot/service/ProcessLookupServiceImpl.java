package com.autopilot.service;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProcessLookupServiceImpl implements ProcessLookupService {
    @Override
    public List<ProcessInfo> getRunningBackendProcesses() {
        return ProcessHandle.allProcesses()
                .filter(ph -> {
                    String command = ph.info().command().orElse("");
                    boolean isJava = command.endsWith("java") || command.equals("java");
                    if (!isJava) {
                        return false;
                    }
                    String cmdLine = ph.info().commandLine().orElse("");
                    return cmdLine.contains("autopilot-backend-0.0.1-SNAPSHOT.jar") 
                        || cmdLine.contains("com.autopilot.AutopilotBackendApplication");
                })
                .map(ph -> new ProcessInfo(String.valueOf(ph.pid()), ph.info().commandLine().orElse("")))
                .collect(Collectors.toList());
    }
}
