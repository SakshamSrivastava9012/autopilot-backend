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
}
