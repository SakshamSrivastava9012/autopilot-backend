package com.autopilot.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AssetManifestEntry {
    private String logicalPath;
    private String containerPath;
    private String publicUrl;
    private boolean requiresPrefix;
    private boolean cacheable;
}
