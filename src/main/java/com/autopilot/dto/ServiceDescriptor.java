package com.autopilot.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDescriptor {
    String id;
    String name;
    String language;
    String framework;
    String type;
    ServiceRole role;
    String dockerfile;
    String buildCommand;
    String startCommand;
    String runtime;
    String healthEndpoint;
    int port;
    String routePrefix;
    List<String> dependencies;
    int startupOrder;
    Map<String, String> environment;
    String serviceRoot;
    String dockerContext;
}
