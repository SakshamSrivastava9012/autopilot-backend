package com.autopilot.controller;

import com.autopilot.dto.DeployRequest;
import com.autopilot.entity.Deployment;
import com.autopilot.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/deploy")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    // Start deployment
    @PostMapping
    public Deployment deploy(@RequestBody DeployRequest request) {
        return deploymentService.createDeployment(request);
    }

    // Get deployment status
    @GetMapping("/{id}")
    public Deployment getDeployment(@PathVariable String id) {
        return deploymentService.getDeployment(id);
    }

    // List all deployments
    @GetMapping
    public List<Deployment> listDeployments() {
        return deploymentService.getAllDeployments();
    }
}