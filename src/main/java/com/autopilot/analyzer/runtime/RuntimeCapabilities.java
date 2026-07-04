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
public class RuntimeCapabilities {
    private Set<CapabilityType> types;

    public boolean has(CapabilityType type) {
        return types != null && types.contains(type);
    }
}
