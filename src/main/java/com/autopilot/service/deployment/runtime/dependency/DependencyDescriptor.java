package com.autopilot.service.deployment.runtime.dependency;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class DependencyDescriptor {
    private String type; // PostgreSQL, MySQL, Redis, Kafka, etc.
    private String name;
    private boolean required;
    private boolean optional;
    
    private String provider; // EXISTING, PLATFORM_MANAGED, DOCKER_RUNTIME, AUTOMATIC
    private String version;
    private String connectionUri; // E.g., mongodb+srv://...
    private Map<String, String> credentials;
    
    private String network;
    private String healthStrategy;
    private int startupOrder;
    
    private boolean persistent;
    private boolean shared;
    private String backupPolicy;
}
