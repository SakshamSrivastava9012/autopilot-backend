package com.autopilot.service.infrastructure;

import org.springframework.stereotype.Service;

@Service
public class CapacityPlanner {

    public String chooseInstanceType(Integer expectedUsers) {

        if (expectedUsers == null) {
            return "t3.micro";
        }

        if (expectedUsers <= 200) {
            return "t3.micro";
        }

        if (expectedUsers <= 1000) {
            return "t3.small";
        }

        if (expectedUsers <= 5000) {
            return "t3.medium";
        }

        return "t3.large";
    }
}
