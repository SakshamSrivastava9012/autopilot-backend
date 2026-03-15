package com.autopilot.analyzer.cache;

import com.autopilot.analyzer.model.RepoAnalysisResult;

import java.util.concurrent.ConcurrentHashMap;

public class RepoAnalysisCache {

    private final ConcurrentHashMap<String, RepoAnalysisResult> cache =
            new ConcurrentHashMap<>();

    public RepoAnalysisResult get(String key) {

        return cache.get(key);
    }

    public void put(String key, RepoAnalysisResult result) {

        cache.put(key, result);
    }
}