package com.autopilot.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "deployments")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * The user who owns this deployment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    /**
     * Denormalized userId for quick queries without joining.
     */
    @Column(name = "user_id", insertable = false, updatable = false)
    private String userId;

    private String projectName;

    private String repoUrl;
    private String accessUrl;
    private String branch;

    private String status;

    @Column(columnDefinition = "LONGTEXT")
    private String logs;
    private Integer port;

    private String environment;

    // traffic estimate
    private Integer expectedUsers;

    // AWS configuration
    private String awsRoleArn;

    private String awsRegion;

    private String awsAccountId;

    private String ec2KeyPath;

    // Docker / ECR
    private String imageUri;

    // Infrastructure
    private String publicIp;

    // NEW FIELD
    private String ec2InstanceId;

    @Column(name = "assigned_port")
    private Integer assignedPort;

    @Column(name = "base_path")
    private String basePath;
}
