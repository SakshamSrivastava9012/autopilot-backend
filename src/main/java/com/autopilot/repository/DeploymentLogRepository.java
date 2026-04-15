package com.autopilot.repository;

import com.autopilot.entity.DeploymentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, String> {

    /**
     * Fetch all log entries for a deployment, ordered by timestamp ascending.
     */
    List<DeploymentLog> findByDeploymentIdOrderByTimestampAsc(String deploymentId);
}
