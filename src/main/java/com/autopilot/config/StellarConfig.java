package com.autopilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "stellar")
@Data
public class StellarConfig {
    private String provider;
    private String url;
    private String model;
}