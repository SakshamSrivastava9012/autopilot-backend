package com.autopilot.service.deployment.intelligence;

import com.autopilot.analyzer.model.RepoAnalysisResult;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectAnalysis {
    private RepoAnalysisResult repoAnalysis;
    private Integer expectedUsers;
}
