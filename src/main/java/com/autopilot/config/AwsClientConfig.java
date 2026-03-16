package com.autopilot.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class AwsClientConfig {
    // SsmClient is now created dynamically in SSMDeployService
    // using the deployment's role credentials — no static bean needed
}