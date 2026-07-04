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

    @Column(columnDefinition = "LONGTEXT")
    private String customEnvVarsJson;

    // traffic estimate
    private Integer expectedUsers;

    // AWS configuration
    private String awsRoleArn;

    private String awsRegion;

    // MANAGED or BYOC
    @Column(name = "deployment_mode")
    private String deploymentMode;

    private String awsAccountId;

    private String ec2KeyPath;

    // Docker / ECR
    private String imageUri;

    // Infrastructure
    private String publicIp;

    // NEW FIELD
    private String ec2InstanceId;

    @Column(name = "instance_type_override")
    private String instanceTypeOverride;

    @Column(name = "assigned_port")
    private Integer assignedPort;

    @Column(name = "base_path")
    private String basePath;

    @Column(name = "strategy_used")
    private String strategyUsed;

    @Column(name = "build_command")
    private String buildCommand;

    @Column(name = "start_command")
    private String startCommand;

    @Column(name = "runtime_version")
    private String runtimeVersion;

    // Config Intelligence results
    @Column(name = "detected_databases")
    private String detectedDatabases; // comma-separated: "mysql,postgres"

    @Column(name = "detected_caches")
    private String detectedCaches; // comma-separated: "redis,kafka"

    @Column(name = "secret_count")
    private Integer secretCount;

    @Column(name = "env_var_count")
    private Integer envVarCount;

    @Column(name = "secrets_arn")
    private String secretsArn; // AWS Secrets Manager ARN

    @Column(name = "rds_endpoint")
    private String rdsEndpoint; // auto-provisioned RDS endpoint

    @Column(name = "deployed_services_json", columnDefinition = "LONGTEXT")
    private String deployedServicesJson;
}

