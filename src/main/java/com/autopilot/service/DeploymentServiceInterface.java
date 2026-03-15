package com.autopilot.service;

import com.autopilot.dto.DeployRequest;
import com.autopilot.entity.Deployment;

import java.util.List;

public interface DeploymentServiceInterface {

    Deployment createDeployment(DeployRequest request);

    Deployment getDeployment(String id);

    List<Deployment> getAllDeployments();
}