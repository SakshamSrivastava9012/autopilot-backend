package com.autopilot.service.deployment.intelligence;

public interface InstanceSelector {
    InstanceRecommendation recommend(ProjectAnalysis analysis);
}
