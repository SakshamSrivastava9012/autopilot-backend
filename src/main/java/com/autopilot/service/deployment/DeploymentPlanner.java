package com.autopilot.service.deployment;

import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.dto.DeploymentService;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DeploymentPlanner {

    public List<ServiceConfig> planDeploymentOrder(List<ServiceConfig> services) {
        List<ServiceConfig> ordered = new ArrayList<>(services);
        ordered.sort((s1, s2) -> {
            int rank1 = getRoleRank(s1.getRole());
            int rank2 = getRoleRank(s2.getRole());
            return Integer.compare(rank1, rank2);
        });
        return ordered;
    }

    public List<DeploymentService> planServiceOrder(List<DeploymentService> services) {
        List<DeploymentService> ordered = new ArrayList<>(services);
        ordered.sort((s1, s2) -> {
            int rank1 = getRoleRank(s1.getRole());
            int rank2 = getRoleRank(s2.getRole());
            return Integer.compare(rank1, rank2);
        });
        return ordered;
    }

    public List<com.autopilot.dto.ServiceDescriptor> planServiceDescriptorOrder(List<com.autopilot.dto.ServiceDescriptor> services) {
        Map<String, com.autopilot.dto.ServiceDescriptor> serviceMap = new HashMap<>();
        for (com.autopilot.dto.ServiceDescriptor s : services) {
            serviceMap.put(s.getId(), s);
        }

        List<com.autopilot.dto.ServiceDescriptor> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> tempVisited = new HashSet<>();

        for (com.autopilot.dto.ServiceDescriptor s : services) {
            if (!visited.contains(s.getId())) {
                visit(s.getId(), serviceMap, visited, tempVisited, result);
            }
        }

        result.sort(Comparator.comparingInt(s -> getGranularRoleRank(s.getRole())));
        return result;
    }

    private void visit(String id, Map<String, com.autopilot.dto.ServiceDescriptor> serviceMap, Set<String> visited, Set<String> tempVisited, List<com.autopilot.dto.ServiceDescriptor> result) {
        if (tempVisited.contains(id)) {
            return;
        }
        if (!visited.contains(id)) {
            tempVisited.add(id);
            com.autopilot.dto.ServiceDescriptor s = serviceMap.get(id);
            if (s != null && s.getDependencies() != null) {
                for (String dep : s.getDependencies()) {
                    if (serviceMap.containsKey(dep)) {
                        visit(dep, serviceMap, visited, tempVisited, result);
                    }
                }
            }
            tempVisited.remove(id);
            visited.add(id);
            result.add(s);
        }
    }

    private int getRoleRank(String role) {
        if (role == null) return 10;
        switch (role.toLowerCase()) {
            case "database":
            case "cache":
                return 1;
            case "api":
            case "backend":
                return 2;
            case "worker":
            case "cron":
                return 3;
            case "frontend":
            case "gateway":
                return 4;
            default:
                return 5;
        }
    }

    private int getGranularRoleRank(com.autopilot.dto.ServiceRole role) {
        if (role == null) return 10;
        switch (role) {
            case DATABASE:
            case CACHE:
            case MESSAGE_BROKER:
            case OBJECT_STORAGE:
                return 1;
            case API:
            case GRAPHQL:
            case WEBSOCKET:
                return 2;
            case WORKER:
            case CRON:
                return 3;
            case STATIC_SITE:
            case SPA:
            case SSR:
            case PROXY:
                return 4;
            default:
                return 5;
        }
    }
}
