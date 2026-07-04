package com.autopilot.service.deployment.intelligence;

import com.autopilot.analyzer.model.RepoAnalysisResult;
import com.autopilot.analyzer.model.ServiceConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InstanceSelectorImpl implements InstanceSelector {

    @Override
    public InstanceRecommendation recommend(ProjectAnalysis analysis) {
        if (analysis == null || analysis.getRepoAnalysis() == null) {
            return InstanceRecommendation.builder()
                    .instanceType("t3.micro")
                    .estimatedRam("1.0 GB")
                    .estimatedCpu("1 vCPU")
                    .estimatedCost(7.50)
                    .confidenceScore(80)
                    .reason("Default recommendation for empty or simple project.")
                    .build();
        }

        RepoAnalysisResult repoAnalysis = analysis.getRepoAnalysis();
        List<ServiceConfig> services = repoAnalysis.getServices();
        if (services == null) {
            services = new ArrayList<>();
        }

        double ramMb = 400.0; // Ubuntu + Docker + SSM Agent base overhead
        double cpuVcpu = 0.1;

        boolean hasSpringBoot = false;
        boolean hasReact = false;
        boolean hasNode = false;
        boolean hasMysql = false;
        boolean hasPostgres = false;
        boolean hasRedis = false;
        boolean hasMongo = false;
        int jvmServiceCount = 0;

        for (ServiceConfig s : services) {
            String fw = s.getFramework() != null ? s.getFramework().toLowerCase() : "";
            String lang = s.getLanguage() != null ? s.getLanguage().toLowerCase() : "";
            String db = s.getRequiresDatabase() != null ? s.getRequiresDatabase().toUpperCase() : "";

            if (fw.contains("spring") || fw.contains("boot") || lang.contains("java") || lang.contains("kotlin")) {
                hasSpringBoot = true;
                jvmServiceCount++;
                ramMb += 768.0;
                cpuVcpu += 0.5;
            } else if (fw.contains("react") || fw.contains("nginx") || fw.contains("static") || fw.contains("vite")) {
                hasReact = true;
                ramMb += 64.0;
                cpuVcpu += 0.1;
            } else if (fw.contains("node") || fw.contains("express") || lang.contains("javascript") || lang.contains("typescript")) {
                hasNode = true;
                ramMb += 256.0;
                cpuVcpu += 0.2;
            }

            if (db.contains("MYSQL")) {
                hasMysql = true;
            } else if (db.contains("POSTGRES")) {
                hasPostgres = true;
            } else if (db.contains("MONGO")) {
                hasMongo = true;
            } else if (db.contains("REDIS")) {
                hasRedis = true;
            }
        }

        // Add RAM for local database container requirements (Docker Runtime)
        if (hasMysql) {
            ramMb += 512.0;
            cpuVcpu += 0.5;
        }
        if (hasPostgres) {
            ramMb += 512.0;
            cpuVcpu += 0.5;
        }
        if (hasMongo) {
            ramMb += 512.0;
            cpuVcpu += 0.5;
        }
        if (hasRedis) {
            ramMb += 128.0;
            cpuVcpu += 0.1;
        }

        // Apply traffic overhead: assume +0.5MB and +0.001 vCPU per expected user
        Integer expectedUsers = analysis.getExpectedUsers();
        if (expectedUsers != null && expectedUsers > 0) {
            ramMb += (expectedUsers * 0.5);
            cpuVcpu += (expectedUsers * 0.001);
        }

        // Selection mapping based on memory (threshold = 70% utilization)
        String recommendedType = "t3.micro";
        String ramStr = "1.0 GB";
        String cpuStr = "1 vCPU";
        double cost = 7.50;
        String reason = "Recommended for small workloads and static/Node-based applications.";

        if (jvmServiceCount > 1) {
            recommendedType = "t3.large";
            ramStr = "8.0 GB";
            cpuStr = "2 vCPU";
            cost = 60.00;
            reason = "Multiple JVM services detected. Requiring t3.large to avoid GC overhead and OOM crashes.";
        } else if (ramMb > 5600.0 || cpuVcpu > 2.0) {
            recommendedType = "t3.xlarge";
            ramStr = "16.0 GB";
            cpuStr = "4 vCPU";
            cost = 120.00;
            reason = "High memory/CPU demand projected (exceeds t3.large 70% threshold). Recommending t3.xlarge.";
        } else if (ramMb > 2800.0 || (hasSpringBoot && hasMysql && hasReact) || (hasSpringBoot && hasPostgres)) {
            recommendedType = "t3.medium";
            ramStr = "4.0 GB";
            cpuStr = "2 vCPU";
            cost = 30.00;
            reason = "Spring Boot + Database stack detected. Recommending t3.medium to guarantee high performance and stability.";
        } else if (ramMb > 1400.0 || hasSpringBoot || (hasNode && hasMysql)) {
            recommendedType = "t3.small";
            ramStr = "2.0 GB";
            cpuStr = "2 vCPU";
            cost = 15.00;
            reason = "Standard instance recommendation for Spring Boot or Node.js services with a cache.";
        }

        int confidence = 90;
        if (expectedUsers != null && expectedUsers > 5000) {
            confidence = 95;
        }

        return InstanceRecommendation.builder()
                .instanceType(recommendedType)
                .estimatedRam(String.format("%.1f GB", ramMb / 1024.0))
                .estimatedCpu(String.format("%.2f vCPU", cpuVcpu))
                .estimatedCost(cost)
                .confidenceScore(confidence)
                .reason(reason)
                .build();
    }
}
