package com.autopilot.service.deployment.runtime.dependency;

import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.Map;

@Service
public class DependencyResolver {

    public DependencyDescriptor resolveDependency(String type, String name, Map<String, String> rawEnv, String userPreference) {
        System.out.println("🔍 Resolving Dependency: " + type + " (name=" + name + ")");
        
        String provider = userPreference != null ? userPreference : "AUTOMATIC";
        String connectionUri = null;
        
        // Scan rawEnv for existing external connection strings
        if (rawEnv != null) {
            for (Map.Entry<String, String> entry : rawEnv.entrySet()) {
                String val = entry.getValue();
                if (val == null) continue;
                
                if ("mysql".equalsIgnoreCase(type) && (val.startsWith("jdbc:mysql://") || val.startsWith("mysql://"))) {
                    connectionUri = val;
                    if (!val.contains("localhost") && !val.contains("127.0.0.1")) {
                        provider = "EXISTING_EXTERNAL";
                    }
                    break;
                } else if (("postgres".equalsIgnoreCase(type) || "postgresql".equalsIgnoreCase(type)) 
                        && (val.startsWith("jdbc:postgresql://") || val.startsWith("postgresql://"))) {
                    connectionUri = val;
                    if (!val.contains("localhost") && !val.contains("127.0.0.1")) {
                        provider = "EXISTING_EXTERNAL";
                    }
                    break;
                } else if (("mongo".equalsIgnoreCase(type) || "mongodb".equalsIgnoreCase(type)) 
                        && (val.startsWith("mongodb://") || val.startsWith("mongodb+srv://"))) {
                    connectionUri = val;
                    if (!val.contains("localhost") && !val.contains("127.0.0.1")) {
                        provider = "EXISTING_EXTERNAL";
                    }
                    break;
                } else if ("redis".equalsIgnoreCase(type) && val.startsWith("redis://")) {
                    connectionUri = val;
                    if (!val.contains("localhost") && !val.contains("127.0.0.1")) {
                        provider = "EXISTING_EXTERNAL";
                    }
                    break;
                }
            }
        }
        
        return DependencyDescriptor.builder()
                .type(type)
                .name(name)
                .required(true)
                .provider(provider)
                .connectionUri(connectionUri)
                .persistent(true)
                .healthStrategy("TCP")
                .build();
    }

    public DependencyDescriptor resolveDependency(String type, String detectedConfiguration) {
        System.out.println("🔍 Resolving Dependency Configuration (legacy): " + type);
        return resolveDependency(type, type + "-primary", new java.util.HashMap<>(), "AUTOMATIC");
    }
}
