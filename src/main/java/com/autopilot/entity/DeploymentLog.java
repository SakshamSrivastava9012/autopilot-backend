package com.autopilot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@Table(name = "deployment_logs", indexes = {
        @Index(name = "idx_deployment_logs_deployment_id", columnList = "deploymentId")
})
public class DeploymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String deploymentId;

    /**
     * Pipeline stage: CLONING, ANALYZING, BUILDING_IMAGE, etc.
     */
    @Column(nullable = false)
    private String stage;

    /**
     * Log level: INFO, WARN, ERROR, DEBUG
     */
    @Column(nullable = false)
    private String level;

    /**
     * The actual log message.
     */
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String message;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        this.timestamp = LocalDateTime.now();
    }

    public DeploymentLog(String deploymentId, String stage, String level, String message) {
        this.deploymentId = deploymentId;
        this.stage = stage;
        this.level = level;
        this.message = message;
    }
}
