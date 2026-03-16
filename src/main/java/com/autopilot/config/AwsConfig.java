package com.autopilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;

@Configuration
public class AwsConfig {

    @Bean
    public StsClient stsClient() {
        return StsClient.builder()
                .region(Region.AP_SOUTH_1)
                .build();
    }
}