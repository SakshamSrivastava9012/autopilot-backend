package com.autopilot.analyzer;

import com.autopilot.analyzer.detectors.FrameworkDetector;
import com.autopilot.analyzer.model.RepoAnalysisResult;
import com.autopilot.analyzer.model.ServiceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Production-grade Repository Analyzer with 4-tier detection.
 *
 * Tier 1: Native Dockerfile (validate before using)
 * Tier 2: Rule-based framework plugins
 * Tier 3: AI analysis via Stellar LLM
 * Tier 4: Generic fallback (NEVER FAILS)
 *
 * After detection, performs:
 * - Java version extraction from pom.xml/build.gradle
 * - Dockerfile validation
 * - Confidence scoring adjustment
 */
@Service
@RequiredArgsConstructor
public class RepoAnalyzerService {

    private final UniversalAnalyzerService universalAnalyzer;

    public RepoAnalysisResult analyzeWorkspace(Path workspace) throws Exception {

        RepoAnalysisResult result = new RepoAnalysisResult();

        List<String> files =
                Files.walk(workspace)
                        .filter(Files::isRegularFile)
                        .map(path -> workspace.relativize(path).toString())
                        .collect(Collectors.toList());

        System.out.println("📂 Workspace contains " + files.size() + " files");

        result.setDockerized(files.stream().anyMatch(f -> f.endsWith("Dockerfile")));

        // ── TIER 1: RULE-BASED TEMPLATES (MONOREPO AWARE) ───────────────
        FrameworkDetector ruleDetector = new FrameworkDetector();
        List<ServiceConfig> templateServices = ruleDetector.detect(files);

        // Filter out DockerPlugin results (since we check for native Dockerfile later if needed)
        templateServices = templateServices.stream()
                .filter(s -> !"DOCKERFILE".equals(s.getStrategyUsed()))
                .collect(Collectors.toList());

        if (!templateServices.isEmpty()) {
            if (templateServices.size() > 1) {
                // Monorepo fully detected!
                System.out.println("📋 TIER 1: MULTIPLE Frameworks Detected (Monorepo) → " + templateServices.size() + " services");
                for (ServiceConfig s : templateServices) {
                    if ("java".equals(s.getLanguage())) {
                        String detectedVersion = detectJavaVersion(workspace, files);
                        if (detectedVersion != null) s.setRuntimeVersion(detectedVersion);
                    }
                    if (s.isDockerfileExists()) {
                        String cleanPath = (s.getPath() == null || s.getPath().equals(".") || s.getPath().equals("/")) ? "" : s.getPath();
                        Path dockerfilePath = workspace.resolve(cleanPath).resolve("Dockerfile").normalize();
                        Integer exPort = extractPortFromDockerfileOrNull(dockerfilePath);
                        if (exPort != null) s.setPort(exPort);
                    }
                }
                result.setServices(templateServices);
                result.setMonoRepo(true);
                return result;
            } else {
                ServiceConfig primary = templateServices.get(0);
                if ("java".equals(primary.getLanguage())) {
                    String detectedVersion = detectJavaVersion(workspace, files);
                    if (detectedVersion != null) {
                        primary.setRuntimeVersion(detectedVersion);
                        System.out.println("🔍 Detected Java version from pom.xml: " + detectedVersion);
                    }
                }
                if (primary.isDockerfileExists()) {
                    String cleanPath = (primary.getPath() == null || primary.getPath().equals(".") || primary.getPath().equals("/")) ? "" : primary.getPath();
                    Path dockerfilePath = workspace.resolve(cleanPath).resolve("Dockerfile").normalize();
                    Integer exPort = extractPortFromDockerfileOrNull(dockerfilePath);
                    if (exPort != null) primary.setPort(exPort);
                }
                System.out.println("📋 TIER 1: Template detected → " + primary.getFramework()
                        + " (runtime: " + primary.getRuntimeVersion() + ")");
                result.setServices(templateServices);
                result.setMonoRepo(false);
                return result;
            }
        }

        // ── TIER 2: NATIVE DOCKERFILE (SINGLE SERVICE FALLBACK) ──────────
        for (String file : files) {
            if (file.equals("Dockerfile") || file.endsWith("/Dockerfile")) {
                Path dockerfilePath = workspace.resolve(file);
                if (validateNativeDockerfile(dockerfilePath)) {
                    ServiceConfig dockerSvc = new ServiceConfig();
                    dockerSvc.setStrategyUsed("DOCKERFILE");
                    dockerSvc.setFramework("docker");
                    dockerSvc.setName("docker-service");
                    dockerSvc.setConfidence(100);

                    String path = file.replace("/Dockerfile", "").replace("Dockerfile", ".");
                    dockerSvc.setPath(path);
                    dockerSvc.setDockerfileExists(true);

                    // Try to extract EXPOSE port from native Dockerfile
                    dockerSvc.setPort(extractPortFromDockerfile(dockerfilePath));

                    System.out.println("💎 TIER 2: Valid native Dockerfile at " + path);
                    result.setServices(List.of(dockerSvc));
                    return result;
                } else {
                    System.out.println("⚠️ TIER 2: Dockerfile found but INVALID — skipping");
                }
            }
        }

        // ── TIER 3: AI REFLECTION (LLM) ──────────────────────────────────
        try {
            System.out.println("🤖 TIER 3: Consulting Stellar AI...");
            ServiceConfig aiDetect = universalAnalyzer.analyzeTree(files);
            if (aiDetect != null && aiDetect.getFramework() != null) {
                aiDetect.setStrategyUsed("AI_GENERATED");
                if (aiDetect.getConfidence() == null) aiDetect.setConfidence(60);

                System.out.println("✅ TIER 3: AI detected → " + aiDetect.getFramework());
                result.setServices(List.of(aiDetect));
                return result;
            }
        } catch (Exception e) {
            System.err.println("⚠️ TIER 3 Failed: " + e.getMessage());
        }

        // ── TIER 4: GENERIC FALLBACK (NEVER FAILS) ──────────────────────
        System.out.println("🛡️ TIER 4: Using generic fallback");
        ServiceConfig fallback = buildGenericFallback(files);
        result.setServices(List.of(fallback));
        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPER METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Validate that a native Dockerfile contains the minimum required instructions.
     */
    private boolean validateNativeDockerfile(Path dockerfilePath) {
        try {
            String content = Files.readString(dockerfilePath);
            return content.contains("FROM") && (content.contains("CMD") || content.contains("ENTRYPOINT"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Extract EXPOSE port from a Dockerfile.
     */
     private Integer extractPortFromDockerfile(Path dockerfilePath) {
        Integer extracted = extractPortFromDockerfileOrNull(dockerfilePath);
        return extracted != null ? extracted : 8080; // safe default
    }

    private Integer extractPortFromDockerfileOrNull(Path dockerfilePath) {
        try {
            String content = Files.readString(dockerfilePath);
            Matcher m = Pattern.compile("EXPOSE\\s+(\\d+)").matcher(content);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Detect the Java version from pom.xml or build.gradle.
     * Scans for <java.version>, <maven.compiler.source>, or sourceCompatibility.
     */
    private String detectJavaVersion(Path workspace, List<String> files) {
        // Try pom.xml first
        for (String file : files) {
            if (file.endsWith("pom.xml")) {
                try {
                    String content = Files.readString(workspace.resolve(file));

                    // Pattern 1: <java.version>21</java.version>
                    Matcher m1 = Pattern.compile("<java\\.version>(\\d+)</java\\.version>").matcher(content);
                    if (m1.find()) return m1.group(1);

                    // Pattern 2: <maven.compiler.source>17</maven.compiler.source>
                    Matcher m2 = Pattern.compile("<maven\\.compiler\\.source>(\\d+)</maven\\.compiler\\.source>").matcher(content);
                    if (m2.find()) return m2.group(1);

                    // Pattern 3: <release>21</release>
                    Matcher m3 = Pattern.compile("<release>(\\d+)</release>").matcher(content);
                    if (m3.find()) return m3.group(1);

                } catch (IOException e) {
                    System.err.println("⚠️ Could not read pom.xml for version detection: " + e.getMessage());
                }
            }
        }

        // Try build.gradle
        for (String file : files) {
            if (file.endsWith("build.gradle")) {
                try {
                    String content = Files.readString(workspace.resolve(file));

                    // Pattern: sourceCompatibility = '17' or sourceCompatibility = 17
                    Matcher m = Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?(\\d+)").matcher(content);
                    if (m.find()) return m.group(1);

                    // Pattern: JavaVersion.VERSION_21
                    Matcher m2 = Pattern.compile("JavaVersion\\.VERSION_(\\d+)").matcher(content);
                    if (m2.find()) return m2.group(1);

                } catch (IOException e) {
                    System.err.println("⚠️ Could not read build.gradle for version detection: " + e.getMessage());
                }
            }
        }

        return null; // use plugin default
    }

    /**
     * Build a generic fallback ServiceConfig by guessing from file extensions.
     */
    private ServiceConfig buildGenericFallback(List<String> files) {
        ServiceConfig fb = new ServiceConfig();
        fb.setName("generic-app");
        fb.setFramework("generic");
        fb.setStrategyUsed("FALLBACK");
        fb.setPath(".");
        fb.setPort(8080);
        fb.setConfidence(20);

        // Try to guess language from file extensions
        long javaCount = files.stream().filter(f -> f.endsWith(".java")).count();
        long jsCount = files.stream().filter(f -> f.endsWith(".js") || f.endsWith(".ts")).count();
        long pyCount = files.stream().filter(f -> f.endsWith(".py")).count();
        long goCount = files.stream().filter(f -> f.endsWith(".go")).count();

        if (javaCount > jsCount && javaCount > pyCount && javaCount > goCount) {
            fb.setLanguage("java");
            fb.setRuntimeVersion("17");
            fb.setBuildCommand("mvn clean package -DskipTests");
            fb.setStartCommand("java -jar target/*.jar");
        } else if (jsCount > pyCount && jsCount > goCount) {
            fb.setLanguage("javascript");
            fb.setRuntimeVersion("20");
            fb.setBuildCommand("npm install && npm run build");
            fb.setStartCommand("[\"npm\", \"start\"]");
        } else if (pyCount > goCount) {
            fb.setLanguage("python");
            fb.setRuntimeVersion("3.10");
            fb.setBuildCommand("pip install -r requirements.txt || true");
            fb.setStartCommand("[\"python\", \"app.py\"]");
        } else if (goCount > 0) {
            fb.setLanguage("go");
            fb.setRuntimeVersion("1.22");
            fb.setBuildCommand("go build -o server .");
            fb.setStartCommand("[\"./server\"]");
        } else {
            fb.setLanguage("unknown");
            fb.setBuildCommand("echo 'Unknown project — skipping build'");
            fb.setStartCommand("[\"ls\", \"-la\"]");
        }

        return fb;
    }
}