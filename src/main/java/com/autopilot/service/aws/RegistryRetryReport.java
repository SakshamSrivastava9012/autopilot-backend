package com.autopilot.service.aws;

import lombok.Builder;
import lombok.Data;
import java.util.Set;

@Data
@Builder
public class RegistryRetryReport {
    private String sessionId;
    private int attempt;
    private long backoffDurationMs;
    private String errorTrigger;
    private Set<String> layersExistedBeforeRetry;
    private Set<String> layersUploadedDuringAttempt;
}
