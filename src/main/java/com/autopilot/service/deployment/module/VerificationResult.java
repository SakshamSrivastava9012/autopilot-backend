package com.autopilot.service.deployment.module;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class VerificationResult {
    private boolean successful;
    private List<String> warnings;
    private List<String> errors;

    public static VerificationResult success() {
        return VerificationResult.builder()
                .successful(true)
                .warnings(List.of())
                .errors(List.of())
                .build();
    }

    public static VerificationResult failure(List<String> errors) {
        return VerificationResult.builder()
                .successful(false)
                .warnings(List.of())
                .errors(errors)
                .build();
    }
}
