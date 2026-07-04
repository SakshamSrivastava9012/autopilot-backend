package com.autopilot.service.deployment.runtime.lifecycle;

import org.springframework.stereotype.Service;

@Service
public class RuntimeLifecycleEngine {

    public RuntimeLifecycleReport executeLifecycle(RuntimeReadinessContract contract) {
        System.out.println("🔄 Starting Runtime Lifecycle Engine...");
        
        RuntimeTransitionTimeline timeline = RuntimeTransitionTimeline.builder().build();
        ApplicationRuntimeState currentState = ApplicationRuntimeState.INFRASTRUCTURE_READY;
        
        try {
            currentState = advance(currentState, ApplicationRuntimeState.IMAGE_AVAILABLE, timeline);
            currentState = advance(currentState, ApplicationRuntimeState.CONTAINER_CREATED, timeline);
            currentState = advance(currentState, ApplicationRuntimeState.CONTAINER_RUNNING, timeline);
            currentState = advance(currentState, ApplicationRuntimeState.PROCESS_STARTED, timeline);
            currentState = advance(currentState, ApplicationRuntimeState.APPLICATION_BOOTSTRAPPING, timeline);
            currentState = advance(currentState, ApplicationRuntimeState.PORT_BOUND, timeline);
            
            // At this point, we negotiate readiness
            negotiateReadiness(contract);
            currentState = advance(currentState, ApplicationRuntimeState.READINESS_CONFIRMED, timeline);
            
            // Health validation tolerates custom codes (e.g., 302, 401, 200)
            negotiateHealth(contract);
            currentState = advance(currentState, ApplicationRuntimeState.HEALTH_AVAILABLE, timeline);
            
            currentState = advance(currentState, ApplicationRuntimeState.READY, timeline);
            currentState = advance(currentState, ApplicationRuntimeState.SERVING_TRAFFIC, timeline);
            
            return RuntimeLifecycleReport.builder()
                    .isHealthy(true)
                    .currentState(currentState)
                    .readinessContract(contract)
                    .timeline(timeline)
                    .build();
                    
        } catch (Exception e) {
            System.err.println("❌ Runtime Transition Failed at state: " + currentState);
            StartupFailureReport failureReport = StartupFailureReport.builder()
                    .lastSuccessfulState(currentState)
                    .failedTransition(getNextState(currentState))
                    .exitCode(137) // mock OOM
                    .oomKilled(true)
                    .dockerLogs("Error: Spring Boot failed to start. Port 8080 already in use.")
                    .timestamp(System.currentTimeMillis())
                    .build();
                    
            return RuntimeLifecycleReport.builder()
                    .isHealthy(false)
                    .currentState(currentState)
                    .readinessContract(contract)
                    .timeline(timeline)
                    .failureReport(failureReport)
                    .build();
        }
    }

    private ApplicationRuntimeState advance(ApplicationRuntimeState from, ApplicationRuntimeState to, RuntimeTransitionTimeline timeline) {
        // Mock transition logic
        System.out.println("  -> Transitioning to " + to);
        timeline.recordTransition(from, to, 150); // 150ms mock duration
        emitEvent(to);
        return to;
    }

    private void negotiateReadiness(RuntimeReadinessContract contract) {
        // Implementation will check contract.getReadinessStrategy()
        // and confirm if the port is bound and the process is alive.
    }

    private void negotiateHealth(RuntimeReadinessContract contract) {
        // Implementation will check contract.getAcceptableStatusCodes()
        // and tolerate expected redirects like 302 or 401 instead of crashing.
    }

    private void emitEvent(ApplicationRuntimeState state) {
        // Event bus integration will go here
    }

    private ApplicationRuntimeState getNextState(ApplicationRuntimeState current) {
        ApplicationRuntimeState[] states = ApplicationRuntimeState.values();
        int nextOrdinal = current.ordinal() + 1;
        return nextOrdinal < states.length ? states[nextOrdinal] : current;
    }
}
