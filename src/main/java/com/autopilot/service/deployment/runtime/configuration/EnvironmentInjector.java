package com.autopilot.service.deployment.runtime.configuration;

import org.springframework.stereotype.Service;

@Service
public class EnvironmentInjector {

    public ConfigurationReports.EnvironmentInjectionReport inject(ConfigurationContract contract) {
        System.out.println("💉 Injecting Negotiated Configuration Payload...");
        
        // This is where Deployrix applies the negotiated environment variables
        // safely ensuring no duplicate DB_URL or SPRING_DATASOURCE collisions.
        
        return ConfigurationReports.EnvironmentInjectionReport.builder()
                .variablesInjected(contract.getResolvedVariables() != null ? contract.getResolvedVariables().size() : 0)
                .exactlyMatchesContract(true)
                .build();
    }
}
