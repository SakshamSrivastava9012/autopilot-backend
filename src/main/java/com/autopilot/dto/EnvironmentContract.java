package com.autopilot.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentContract {
    private Map<String, String> requiredVariables;
    private Map<String, String> optionalVariables;
    private Set<String> injectedSecrets;
    private Map<String, String> resolvedRuntimeVariables;
}
