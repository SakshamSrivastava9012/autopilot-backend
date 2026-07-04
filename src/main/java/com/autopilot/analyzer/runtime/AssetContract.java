package com.autopilot.analyzer.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetContract {
    private List<String> staticDirectories;
    private List<String> publicDirectories;
    private List<String> immutableAssetPrefixes;
    private boolean requiresPrefixRewrite;

    // Kept for backward compatibility
    private Set<String> fileExtensions;
    private Set<String> requiredAssets;
}
