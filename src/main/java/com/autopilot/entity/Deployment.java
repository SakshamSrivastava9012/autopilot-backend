package com.autopilot.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "deployments")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String projectName;

    private String repoUrl;

    private String branch;

    private String status;

    @Column(length = 5000)
    private String logs;

    private Integer port;

    private String environment;

    // AWS info
    private String awsRoleArn;

    private String awsRegion;

    // NEW FIELD → stores pushed ECR image
    private String imageUri;
}
