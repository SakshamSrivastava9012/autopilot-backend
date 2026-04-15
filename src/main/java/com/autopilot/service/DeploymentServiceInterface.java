package com.autopilot.service;

import com.autopilot.dto.DeployRequest;
import com.autopilot.entity.Deployment;
import com.autopilot.entity.User;

import java.util.List;

public interface DeploymentServiceInterface {

    Deployment createDeployment(DeployRequest request, User user);

    Deployment getDeployment(String id, User user);

    List<Deployment> getUserDeployments(User user);
}