package com.autopilot.controller;

import com.autopilot.dto.DeployRequest;
import com.autopilot.entity.Deployment;
import com.autopilot.entity.User;
import com.autopilot.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/deploy")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    /**
     * Start a new deployment — linked to the authenticated user.
     */
    @PostMapping
    public Deployment deploy(@RequestBody DeployRequest request, Authentication auth) {
        return deploymentService.createDeployment(request, extractUser(auth));
    }

    /**
     * Get a single deployment — only if it belongs to the authenticated user.
     */
    @GetMapping("/{id}")
    public Deployment getDeployment(@PathVariable String id, Authentication auth) {
        return deploymentService.getDeployment(id, extractUser(auth));
    }

    /**
     * Delete/Destroy a deployment.
     */
    @DeleteMapping("/{id}")
    public Deployment deleteDeployment(@PathVariable String id, Authentication auth) {
        return deploymentService.deleteDeployment(id, extractUser(auth));
    }

    /**
     * List all deployments belonging to the authenticated user.
     */
    @GetMapping
    public List<Deployment> listDeployments(Authentication auth) {
        return deploymentService.getUserDeployments(extractUser(auth));
    }

    // ── Helper ──────────────────────────────────────────────────────────

    /**
     * Extract the User entity from the Spring Security authentication context.
     * The JwtAuthFilter sets the DB User object as the principal.
     */
    private User extractUser(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return (User) auth.getPrincipal();
    }
}