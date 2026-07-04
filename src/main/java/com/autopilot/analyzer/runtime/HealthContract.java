package com.autopilot.analyzer.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthContract {
    private String checkPath;
    private Set<Integer> expectedStatusCodes;
    private Set<String> expectedMimeTypes;
}
