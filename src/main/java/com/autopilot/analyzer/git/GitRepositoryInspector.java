package com.autopilot.analyzer.git;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GitRepositoryInspector {

    public List<String> listFiles(String repoUrl, String branch) throws Exception {

        List<String> files = new ArrayList<>();

        Process process = Runtime.getRuntime().exec(
                new String[]{
                        "bash","-c",
                        "git ls-tree -r --name-only " + repoUrl + " " + branch
                });

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

        String line;

        while ((line = reader.readLine()) != null) {

            files.add(line);
        }

        process.waitFor();

        return files;
    }
}