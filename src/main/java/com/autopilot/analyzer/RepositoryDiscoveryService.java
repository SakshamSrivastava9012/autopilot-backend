package com.autopilot.analyzer;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RepositoryDiscoveryService {

    private static final List<String> MANIFEST_FILES = List.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "package.json", "go.mod",
            "requirements.txt", "Pipfile", "pyproject.toml", "Cargo.toml",
            "composer.json", "Gemfile", "Dockerfile", "index.html"
    );

    public Set<Path> discoverServiceRoots(Path workspace) throws IOException {
        if (!Files.exists(workspace)) {
            return Collections.emptySet();
        }

        // 1. Gather all files excluding dependency/build dirs
        List<Path> allFiles = Files.walk(workspace)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    for (Path element : workspace.relativize(path)) {
                        String name = element.toString();
                        if (name.equals("node_modules") || name.equals(".git") ||
                            name.equals("target") || name.equals("build") || name.equals(".gradle")) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 2. Identify potential service root directories containing project manifest/build files
        Set<Path> serviceDirs = new HashSet<>();
        for (Path file : allFiles) {
            String filename = file.getFileName().toString();
            if (MANIFEST_FILES.contains(filename) || filename.endsWith(".sln") || filename.endsWith(".csproj")) {
                Path parent = workspace.relativize(file.getParent());
                serviceDirs.add(parent == null ? Path.of("") : parent);
            }
        }

        // Fallback to workspace root if no service directories are detected
        if (serviceDirs.isEmpty()) {
            serviceDirs.add(Path.of(""));
        }

        // 3. Monorepo / Workspace Filter: Filter out parent directories that are monorepo managers/orchestrators
        Set<Path> finalServiceDirs = new HashSet<>();
        for (Path dir : serviceDirs) {
            boolean hasNestedService = serviceDirs.stream().anyMatch(other -> !other.equals(dir) && isNested(other, dir));
            if (hasNestedService && isMonorepoWorkspaceOrchestrator(workspace.resolve(dir))) {
                continue;
            }
            finalServiceDirs.add(dir);
        }

        return finalServiceDirs;
    }

    private boolean isMonorepoWorkspaceOrchestrator(Path absDir) {
        if (Files.exists(absDir.resolve("pnpm-workspace.yaml"))) return true;
        if (Files.exists(absDir.resolve("nx.json")) || Files.exists(absDir.resolve("lerna.json"))) return true;

        Path pkgJson = absDir.resolve("package.json");
        if (Files.exists(pkgJson)) {
            try {
                String content = Files.readString(pkgJson);
                if (content.contains("\"workspaces\"") || content.contains("\"private\": true")) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        Path pomXml = absDir.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            try {
                String content = Files.readString(pomXml);
                if (content.contains("<modules>") || content.contains("<packaging>pom</packaging>")) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        if (Files.exists(absDir.resolve("settings.gradle")) || Files.exists(absDir.resolve("settings.gradle.kts"))) {
            return true;
        }
        return false;
    }

    private boolean isNested(Path child, Path parent) {
        if (parent.toString().isEmpty() || parent.toString().equals(".")) {
            return !child.toString().isEmpty() && !child.toString().equals(".");
        }
        return child.startsWith(parent) && !child.equals(parent);
    }
}
