package com.autopilot.repository;

import com.autopilot.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeploymentRepository extends JpaRepository<Deployment, String> {
    @Query("SELECT MAX(d.assignedPort) FROM Deployment d")
    Integer findMaxAssignedPort();
    List<Deployment> findByStatus(String status);
    List<Deployment> findByStatusAndEc2InstanceId(String status, String ec2InstanceId);

    /**
     * Fetch all deployments belonging to a specific user.
     */
    List<Deployment> findByUserId(String userId);

    /**
     * Fetch a single deployment only if it belongs to the given user (ownership check).
     */
    java.util.Optional<Deployment> findByIdAndUserId(String id, String userId);
}