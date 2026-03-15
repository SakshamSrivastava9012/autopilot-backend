package com.autopilot.analyzer;

import com.autopilot.analyzer.cache.RepoAnalysisCache;
import com.autopilot.analyzer.detectors.FrameworkDetector;
import com.autopilot.analyzer.git.GitRepositoryInspector;
import com.autopilot.analyzer.model.RepoAnalysisResult;
import com.autopilot.analyzer.model.ServiceConfig;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class RepoAnalyzerService {

    private final GitRepositoryInspector inspector = new GitRepositoryInspector();
    private final FrameworkDetector detector = new FrameworkDetector();
    private final RepoAnalysisCache cache = new RepoAnalysisCache();

    public RepoAnalysisResult analyze(String repoUrl, String branch) throws Exception {

        String cacheKey = repoUrl + ":" + branch;

        RepoAnalysisResult cached = cache.get(cacheKey);

        if (cached != null) {
            return cached;
        }

        // get repository file tree (no cloning)
        List<String> files = inspector.listFiles(repoUrl, branch);

        // detect services
        List<ServiceConfig> services = detector.detect(files);

        RepoAnalysisResult result = new RepoAnalysisResult();

        result.setServices(services);

        // check if repo already contains Dockerfile
        result.setDockerized(
                files.stream().anyMatch(f -> f.endsWith("Dockerfile"))
        );

        // detect monorepo
        result.setMonoRepo(services.size() > 1);

        cache.put(cacheKey, result);

        return result;
    }
    public RepoAnalysisResult analyzeWorkspace(Path workspace) throws Exception {

        RepoAnalysisResult result = new RepoAnalysisResult();

        // detect services inside workspace
        List<ServiceConfig> services = detector.detectWorkspace(workspace);
        result.setServices(services);

        return result;
    }
}