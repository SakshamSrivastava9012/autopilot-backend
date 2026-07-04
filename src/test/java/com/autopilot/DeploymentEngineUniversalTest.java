package com.autopilot;

import com.autopilot.analyzer.adapters.FrameworkAdapterRegistry;
import com.autopilot.analyzer.adapters.ViteAdapter;
import com.autopilot.analyzer.adapters.SpringBootAdapter;
import com.autopilot.analyzer.model.DeploymentManifest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentEngineUniversalTest {

    @Test
    void testViteAdapterMatchingAndManifest() {
        ViteAdapter adapter = new ViteAdapter();
        Path dummyPath = Path.of("/mock/vite-project");
        List<String> files = List.of("package.json", "package-lock.json", "src/App.jsx");

        // Since containsDependency reads package.json from disk, we will mock/verify matches behavior or test properties.
        // We will directly verify generateManifest fields:
        DeploymentManifest manifest = adapter.generateManifest(dummyPath, files, new HashMap<>());
        assertEquals("vite", manifest.getFramework());
        assertEquals("Static", manifest.getRuntime());
        assertEquals("npm", manifest.getPackageManager());
        assertEquals("npm install", manifest.getInstallCommand());
        assertEquals("npm run build", manifest.getBuildCommand());
        assertEquals("npx serve -s dist -l 3000", manifest.getStartCommand());
        assertEquals("dist", manifest.getOutputDirectory());
        assertEquals(3000, manifest.getPort());
    }

    @Test
    void testSpringBootAdapterManifest() {
        SpringBootAdapter adapter = new SpringBootAdapter();
        Path dummyPath = Path.of("/mock/sb-project");
        List<String> files = List.of("pom.xml", "src/main/java/App.java");

        DeploymentManifest manifest = adapter.generateManifest(dummyPath, files, new HashMap<>());
        assertEquals("spring-boot", manifest.getFramework());
        assertTrue(manifest.getRuntime().contains("Java"));
        assertEquals("maven", manifest.getPackageManager());
        assertEquals("./mvnw clean package -DskipTests", manifest.getBuildCommand());
        assertEquals("java -jar target/*.jar", manifest.getStartCommand());
        assertEquals("target", manifest.getOutputDirectory());
        assertEquals(8080, manifest.getPort());
        assertEquals("/health", manifest.getHealthCheckPath());
    }

    @Test
    void testAdapterRegistryResolution() {
        FrameworkAdapterRegistry registry = new FrameworkAdapterRegistry();
        Path dummyPath = Path.of("/mock");
        
        // Registering a custom dynamic adapter
        registry.registerAdapter(new com.autopilot.analyzer.adapters.FrameworkAdapter() {
            @Override
            public boolean matches(Path workspace, List<String> relativeFiles) {
                return relativeFiles.contains("special-file.txt");
            }
            @Override
            public String detect(Path workspace, List<String> relativeFiles) {
                return "SpecialCustom";
            }
            @Override
            public String buildInfo(Path workspace, List<String> relativeFiles) {
                return "Custom special details";
            }
            @Override
            public DeploymentManifest generateManifest(Path workspace, List<String> relativeFiles, java.util.Map<String, String> envVars) {
                return DeploymentManifest.builder()
                        .framework("custom-special")
                        .runtime("CustomEnv")
                        .installCommand("echo setup")
                        .buildCommand("echo build")
                        .startCommand("echo start")
                        .outputDirectory("out")
                        .port(9000)
                        .build();
            }
        });

        var match = registry.findMatchingAdapter(dummyPath, List.of("special-file.txt"));
        assertTrue(match.isPresent());
        assertEquals("SpecialCustom", match.get().detect(dummyPath, List.of("special-file.txt")));
        
        DeploymentManifest manifest = match.get().generateManifest(dummyPath, List.of("special-file.txt"), new HashMap<>());
        assertEquals("custom-special", manifest.getFramework());
        assertEquals(9000, manifest.getPort());
    }
}
