package com.autopilot.service.deployment.runtime.lifecycle;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.ArrayList;

@Data
@Builder
public class RuntimeTransitionTimeline {
    @Builder.Default
    private List<Transition> transitions = new ArrayList<>();
    
    public void recordTransition(ApplicationRuntimeState from, ApplicationRuntimeState to, long durationMs) {
        transitions.add(new Transition(from, to, durationMs, System.currentTimeMillis()));
    }

    @Data
    public static class Transition {
        private final ApplicationRuntimeState from;
        private final ApplicationRuntimeState to;
        private final long durationMs;
        private final long timestamp;
    }
}
