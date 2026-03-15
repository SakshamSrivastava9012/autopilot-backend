package com.autopilot.dto;

import lombok.Data;
import java.util.List;

@Data
public class RepoAnalysisResult {

    private List<ServiceConfig> services;

    private boolean dockerized;
}