package com.autopilot.dto;

import lombok.Data;

@Data
public class ServiceConfig {

    private String name;

    private String framework;

    private String path;

    private String buildCommand;

    private String startCommand;

    private Integer port;
}