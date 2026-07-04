package com.autopilot.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RouteDescriptor {
    String path;
    String targetService;
    String container;
    int internalPort;
    boolean stripPrefix;
    String rewrite;
    Map<String, String> headers;
    String protocol;
    boolean websocket;
    int timeout;
    String healthEndpoint;
}
