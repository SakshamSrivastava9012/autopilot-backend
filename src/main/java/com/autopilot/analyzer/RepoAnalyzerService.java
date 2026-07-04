package com.autopilot.analyzer;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.RepoAnalysisResult;
import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.analyzer.model.DeploymentManifest;
import com.autopilot.analyzer.detectors.DetectorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepoAnalyzerService {

    private final RepositoryDiscoveryService discoveryService;
    private final ProjectClassifier projectClassifier;
    private final ServiceClassifier serviceClassifier;
    private final com.autopilot.service.deployment.validation.StrategyResolver strategyResolver;

    public RepoAnalysisResult analyzeWorkspace(Path workspace) throws Exception {
        RepoAnalysisResult result = new RepoAnalysisResult();

        // 1. Log the repository tree
        System.out.println("🌳 --- REPOSITORY TREE ---");
        List<String> treeLines = new ArrayList<>();
        treeLines.add(workspace.getFileName() != null ? workspace.getFileName().toString() : "workspace");
        printDirectoryTree(workspace, "", 1, treeLines);
        treeLines.forEach(System.out::println);
        System.out.println("🌳 -----------------------");

        // 2. Discover service roots
        Set<Path> finalServiceDirs = discoveryService.discoverServiceRoots(workspace);
        System.out.println("🔍 Discovered service directory roots: " + finalServiceDirs);

        // Walk workspace to collect files for detectors
        List<String> files = Files.walk(workspace)
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
                .map(path -> workspace.relativize(path).toString())
                .collect(Collectors.toList());

        result.setDockerized(files.stream().anyMatch(f -> f.endsWith("Dockerfile")));
        List<ServiceConfig> detectedServices = new ArrayList<>();

        for (Path serviceDir : finalServiceDirs) {
            Path subWorkspace = workspace.resolve(serviceDir);

            // Collect files belonging only to this service directory (excluding child sub-services)
            List<String> dirFiles = new ArrayList<>();
            for (String file : files) {
                Path filePath = Path.of(file);
                boolean underServiceDir = serviceDir.toString().isEmpty() || filePath.startsWith(serviceDir);
                if (underServiceDir) {
                    boolean belongsToSubService = false;
                    for (Path otherDir : finalServiceDirs) {
                        if (!otherDir.equals(serviceDir) && isNested(otherDir, serviceDir) && filePath.startsWith(otherDir)) {
                            belongsToSubService = true;
                            break;
                        }
                    }
                    if (!belongsToSubService) {
                        Path relPath = serviceDir.toString().isEmpty() ? filePath : serviceDir.relativize(filePath);
                        dirFiles.add(relPath.toString());
                    }
                }
            }

            // Perform framework classification
            FrameworkMetadata metadata = projectClassifier.classifyProject(subWorkspace, dirFiles);

            if (metadata != null) {
                ServiceConfig serviceConfig = new ServiceConfig();
                String serviceId = serviceDir.toString().isEmpty() || serviceDir.toString().equals(".")
                        ? metadata.getName()
                        : serviceDir.getFileName().toString();

                serviceConfig.setServiceId(serviceId);
                serviceConfig.setServiceRoot(subWorkspace.toAbsolutePath().normalize().toString());
                serviceConfig.setRepositoryRoot(workspace.toAbsolutePath().normalize().toString());
                serviceConfig.setDockerContext(subWorkspace.toAbsolutePath().normalize().toString());
                serviceConfig.setDockerfileLocation(subWorkspace.resolve("Dockerfile").toAbsolutePath().normalize().toString());

                serviceConfig.setFramework(metadata.getFrameworkType().name().toLowerCase());
                serviceConfig.setLanguage(metadata.getLanguage());
                serviceConfig.setRuntime(metadata.getRuntimeType().name());
                serviceConfig.setPackageManager(metadata.getPackageManager().name());
                serviceConfig.setBuildCommand(metadata.getBuildCommand());
                serviceConfig.setStartCommand(metadata.getStartCommand());
                serviceConfig.setPort(metadata.getPort());

                String outDir = metadata.getOutputDirectory();
                if (outDir != null && !outDir.equals(".")) {
                    serviceConfig.setArtifactLocation(subWorkspace.resolve(outDir).toAbsolutePath().normalize().toString());
                } else {
                    serviceConfig.setArtifactLocation(subWorkspace.toAbsolutePath().normalize().toString());
                }

                String version = metadata.getDefaultRuntimeVersion();
                if ("java".equals(metadata.getLanguage())) {
                    String detectedJava = projectClassifier.detectJavaVersion(subWorkspace, dirFiles);
                    if (detectedJava != null) {
                        version = detectedJava;
                    }
                }
                serviceConfig.setRuntimeVersion(version);
                serviceConfig.setStrategyUsed("STRATEGY_" + metadata.getFrameworkType().name());
                serviceConfig.setDockerfileExists(metadata.isDockerfileExists());

                // Assign the architectural role
                serviceConfig.setRole(serviceClassifier.classifyRole(serviceConfig));

                // Resolve FrameworkStrategy and populate validation metadata fields
                com.autopilot.service.deployment.validation.FrameworkStrategy strategy = strategyResolver.resolve(serviceConfig);
                serviceConfig.setBuildContext(serviceConfig.getServiceRoot());
                serviceConfig.setExpectedManifestFiles(strategy.expectedManifestFiles());
                serviceConfig.setDockerStrategy(strategy.getClass().getSimpleName().replace("FrameworkStrategy", "DockerStrategy"));
                serviceConfig.setValidatorStrategy(strategy.getClass().getSimpleName().replace("FrameworkStrategy", "Validator"));

                // Build deployment manifest
                DeploymentManifest manifest = DeploymentManifest.builder()
                        .framework(metadata.getFrameworkType().name().toLowerCase())
                        .runtime(metadata.getRuntimeType().name())
                        .packageManager(metadata.getPackageManager().name())
                        .installCommand(DetectorUtils.getInstallCommand(metadata.getPackageManager()))
                        .buildCommand(metadata.getBuildCommand())
                        .startCommand(metadata.getStartCommand())
                        .outputDirectory(metadata.getOutputDirectory())
                        .healthCheckPath(metadata.getHealthCheckPath())
                        .port(metadata.getPort())
                        .environmentVariables(new HashMap<>())
                        .build();

                serviceConfig.setDeploymentManifest(manifest);
                detectedServices.add(serviceConfig);
            }
        }

        if (detectedServices.isEmpty()) {
            throw new RuntimeException("No deployable services detected in repository");
        }

        validateServices(workspace, detectedServices);

        result.setServices(detectedServices);
        result.setMonoRepo(detectedServices.size() > 1);

        System.out.println("✅ Detection complete. Found " + detectedServices.size() + " services");
        return result;
    }

    private void validateServices(Path repositoryRoot, List<ServiceConfig> services) {
        System.out.println("📋 --- WORKSPACE ISOLATION REPORT ---");
        System.out.println("Repository Root: " + repositoryRoot.toAbsolutePath().normalize());
        System.out.println("Detected Services:");

        for (ServiceConfig s : services) {
            Path root = Path.of(s.getServiceRoot()).toAbsolutePath().normalize();
            Path context = Path.of(s.getDockerContext()).toAbsolutePath().normalize();
            Path dockerfile = Path.of(s.getDockerfileLocation()).toAbsolutePath().normalize();

            List<String> manifestsFound = new ArrayList<>();
            List<String> manifestCandidates = List.of(
                    "package.json", "pom.xml", "build.gradle", "build.gradle.kts",
                    "go.mod", "requirements.txt", "Pipfile", "pyproject.toml",
                    "Cargo.toml", "composer.json", "Gemfile", "Dockerfile"
            );
            for (String manifest : manifestCandidates) {
                if (Files.exists(root.resolve(manifest))) {
                    manifestsFound.add(manifest);
                }
            }

            System.out.println("  • Service ID: " + s.getServiceId() + " [Role: " + s.getRole() + "]");
            System.out.println("    serviceRoot: " + root);
            System.out.println("    dockerContext: " + context);
            System.out.println("    dockerfile: " + dockerfile);
            System.out.println("    manifest files found: " + manifestsFound);

            if (!Files.exists(root)) {
                throw new IllegalStateException("Invariant failed: serviceRoot does not exist: " + root);
            }
            if (!Files.isDirectory(root)) {
                throw new IllegalStateException("Invariant failed: serviceRoot is not a directory: " + root);
            }
            if (!context.equals(root)) {
                throw new IllegalStateException("Invariant failed: dockerContext (" + context + ") must equal serviceRoot (" + root + ")");
            }

            validateExpectedManifest(s, root);
        }

        // Reject identical service roots
        for (int i = 0; i < services.size(); i++) {
            Path rootI = Path.of(services.get(i).getServiceRoot()).toAbsolutePath().normalize();
            for (int j = i + 1; j < services.size(); j++) {
                Path rootJ = Path.of(services.get(j).getServiceRoot()).toAbsolutePath().normalize();
                if (rootI.equals(rootJ)) {
                    throw new IllegalStateException("Invariant failed: Duplicate service roots detected between Service '"
                            + services.get(i).getServiceId() + "' and Service '"
                            + services.get(j).getServiceId() + "' (" + rootI + ")");
                }
            }
        }
        System.out.println("📋 ----------------------------------");
    }

    private void validateExpectedManifest(ServiceConfig service, Path root) {
        List<String> expected = service.getExpectedManifestFiles();
        if (expected == null || expected.isEmpty()) {
            return;
        }

        boolean found = false;
        for (String m : expected) {
            if (m.contains("*")) {
                try (var stream = Files.find(root, 1, (p, attr) -> p.getFileName().toString().endsWith(m.substring(1)))) {
                    if (stream.findFirst().isPresent()) {
                        found = true;
                        break;
                    }
                } catch (IOException ignored) {}
            } else {
                if (Files.exists(root.resolve(m))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            throw new IllegalStateException("Invariant failed: expected manifest file (one of " + expected + ") is missing in serviceRoot: " + root);
        }
    }

    private boolean isNested(Path child, Path parent) {
        if (parent.toString().isEmpty() || parent.toString().equals(".")) {
            return !child.toString().isEmpty() && !child.toString().equals(".");
        }
        return child.startsWith(parent) && !child.equals(parent);
    }

    private void printDirectoryTree(Path dir, String prefix, int depth, List<String> treeLines) {
        if (depth > 4) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<Path> paths = new ArrayList<>();
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (!name.startsWith(".") && !name.equals("node_modules") && !name.equals("target") && !name.equals("build")) {
                    paths.add(p);
                }
            }
            paths.sort(Comparator.comparing(Path::toString));
            for (int i = 0; i < paths.size(); i++) {
                Path p = paths.get(i);
                boolean isLast = (i == paths.size() - 1);
                String line = prefix + (isLast ? "└── " : "├── ") + p.getFileName().toString();
                treeLines.add(line);
                if (Files.isDirectory(p)) {
                    printDirectoryTree(p, prefix + (isLast ? "    " : "│   "), depth + 1, treeLines);
                }
            }
        } catch (IOException ignored) {}
    }
}