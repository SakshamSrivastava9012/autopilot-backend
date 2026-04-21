package com.autopilot.analyzer.git;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GitRepositoryInspector {

    public List<String> listFiles(String repoUrl, String branch) throws Exception {
        try {
            return executeListFiles(repoUrl, branch);
        } catch (Exception e) {
            System.out.println("⚠️ Branch '" + branch + "' not found for analysis. falling back to default branch...");
            return executeListFiles(repoUrl, ""); // empty branch string in git ls-tree targets the default branch
        }
    }

    private List<String> executeListFiles(String repoUrl, String branch) throws Exception {
        List<String> files = new ArrayList<>();

        String target = (branch != null && !branch.isEmpty()) ? branch : "HEAD";
        
        Process process = Runtime.getRuntime().exec(
                new String[]{
                        "bash","-c",
                        "git ls-tree -r --name-only " + repoUrl + " " + target
                });

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

        String line;
        while ((line = reader.readLine()) != null) {
            files.add(line);
        }

        if (process.waitFor() != 0 && files.isEmpty()) {
            throw new RuntimeException("git ls-tree failed");
        }

        return files;
    }
}