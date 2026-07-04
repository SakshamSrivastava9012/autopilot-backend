package com.autopilot.analyzer.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingContract {
    private boolean historyFallback;
    private boolean preservesPrefix;
    private List<String> backendPrefixes;
    private List<String> publicPrefixes;

    // Kept for backward compatibility
    private String fallbackRedirectPath;
    private String staticAssetsPrefix;
}
