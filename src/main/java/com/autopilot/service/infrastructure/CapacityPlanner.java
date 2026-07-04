package com.autopilot.service.infrastructure;

import com.autopilot.analyzer.model.RepoAnalysisResult;
import com.autopilot.service.deployment.intelligence.InstanceSelector;
import com.autopilot.service.deployment.intelligence.ProjectAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CapacityPlanner {

    private final InstanceSelector instanceSelector;

    public String chooseInstanceType(Integer expectedUsers) {
        if (expectedUsers == null) {
            return "t3.micro";
        }

        if (expectedUsers <= 200) {
            return "t3.micro";
        }

        if (expectedUsers <= 1000) {
            return "t3.small";
        }

        if (expectedUsers <= 5000) {
            return "t3.medium";
        }

        return "t3.large";
    }

    public String chooseInstanceType(RepoAnalysisResult analysis, Integer expectedUsers) {
        if (analysis == null) {
            return chooseInstanceType(expectedUsers);
        }
        ProjectAnalysis proj = ProjectAnalysis.builder()
                .repoAnalysis(analysis)
                .expectedUsers(expectedUsers)
                .build();
        return instanceSelector.recommend(proj).getInstanceType();
    }
}

