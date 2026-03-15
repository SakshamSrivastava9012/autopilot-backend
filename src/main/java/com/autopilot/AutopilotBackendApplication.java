package com.autopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class AutopilotBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutopilotBackendApplication.class, args);
    }

}
