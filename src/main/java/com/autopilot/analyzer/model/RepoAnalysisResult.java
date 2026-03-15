package com.autopilot.analyzer.model;

import lombok.Data;
import java.util.List;

@Data
public class RepoAnalysisResult {

    private List<ServiceConfig> services;
    private boolean dockerized;
    private boolean monoRepo;
}