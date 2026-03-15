package com.autopilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TerraformConfig {

    @Value("${terraform.directory}")
    private String terraformDirectory;

    public String getTerraformDirectory() {
        return terraformDirectory;
    }
}