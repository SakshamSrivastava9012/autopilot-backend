package com.autopilot;

import com.autopilot.analyzer.detectors.FrameworkDetector;
import com.autopilot.analyzer.detectors.FrameworkDetectorFactory;
import com.autopilot.analyzer.detectors.FrameworkRegistry;
import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.FrameworkType;
import com.autopilot.analyzer.model.ServiceConfig;
import com.autopilot.dto.AwsCredentialsDto;
import com.autopilot.entity.Deployment;
import com.autopilot.enums.DeploymentStatus;
import com.autopilot.service.aws.CredentialResolverService;
import com.autopilot.service.deployment.DependencyProvisionService;
import com.autopilot.service.deployment.HealthCheckService;
import com.autopilot.service.deployment.StartupResilienceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AutopilotComponentsIntegrationTest {

    @Autowired
    private CredentialResolverService credentialResolverService;

    @Autowired
    private DependencyProvisionService dependencyProvisionService;

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private StartupResilienceService startupResilienceService;

    @Autowired
    private FrameworkDetectorFactory detectorFactory;

    @Autowired
    private FrameworkRegistry frameworkRegistry;

    @Test
    void testFrameworkDetectionRules(@TempDir Path tempDir) throws IOException {
        // 1. Test Spring Boot Detection
        Path springDir = tempDir.resolve("spring-service");
        Files.createDirectories(springDir);
        Files.writeString(springDir.resolve("pom.xml"), 
                "<project><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot</artifactId></dependency></dependencies></project>");
        
        List<String> springFiles = List.of("spring-service/pom.xml");
        FrameworkDetector springDetector = detectorFactory.getDetector(springDir, springFiles);
        assertNotNull(springDetector);
        FrameworkMetadata springMeta = springDetector.detect(springDir, springFiles);
        assertNotNull(springMeta);
        assertEquals(FrameworkType.SPRING_BOOT, springMeta.getFrameworkType());
        assertEquals("java", springMeta.getLanguage());

        // 2. Test React Vite Detection
        Path reactDir = tempDir.resolve("react-service");
        Files.createDirectories(reactDir);
        Files.writeString(reactDir.resolve("package.json"), 
                "{\"dependencies\": {\"react\": \"^18.2.0\", \"vite\": \"^4.0.0\"}}");
        
        List<String> reactFiles = List.of("react-service/package.json");
        FrameworkDetector reactDetector = detectorFactory.getDetector(reactDir, reactFiles);
        assertNotNull(reactDetector);
        FrameworkMetadata reactMeta = reactDetector.detect(reactDir, reactFiles);
        assertNotNull(reactMeta);
        assertEquals(FrameworkType.REACT_VITE, reactMeta.getFrameworkType());
        assertEquals("javascript", reactMeta.getLanguage());

        // 3. Test Go Detection
        Path goDir = tempDir.resolve("go-service");
        Files.createDirectories(goDir);
        Files.writeString(goDir.resolve("go.mod"), "module go-service");
        
        List<String> goFiles = List.of("go-service/go.mod");
        FrameworkDetector goDetector = detectorFactory.getDetector(goDir, goFiles);
        assertNotNull(goDetector);
        FrameworkMetadata goMeta = goDetector.detect(goDir, goFiles);
        assertNotNull(goMeta);
        assertEquals(FrameworkType.GO, goMeta.getFrameworkType());
    }

    @Test
    void testCredentialResolverManagedAndByoc() {
        Deployment managed = new Deployment();
        managed.setDeploymentMode("MANAGED");
        managed.setAwsRegion("us-west-2");
        
        CredentialResolverService.ResolvedCredentials resManaged = credentialResolverService.resolve(managed);
        assertNotNull(resManaged);
        assertEquals("ap-south-1", resManaged.region());

        Deployment byoc = new Deployment();
        byoc.setDeploymentMode("BYOC");
        byoc.setAwsRoleArn("arn:aws:iam::123456789012:role/AutopilotDeploymentRole");
        byoc.setAwsRegion("us-east-1");

        assertThrows(Exception.class, () -> credentialResolverService.resolve(byoc));
    }

    @Test
    void testDependencyProvisionLocalhostReplacement(@TempDir Path tempDir) throws IOException {
        Path propFile = tempDir.resolve("application.properties");
        String originalContent = "spring.datasource.url=jdbc:mysql://localhost:3306/devdb\n" +
                                 "spring.redis.host=localhost\n" +
                                 "spring.redis.port=6379";
        Files.writeString(propFile, originalContent);

        String rdsEndpoint = "autopilot-rds-db.cxm123456.us-east-1.rds.amazonaws.com:3306";
        String redisEndpoint = "autopilot-redis:6379";

        try {
            java.lang.reflect.Method method = DependencyProvisionService.class.getDeclaredMethod(
                    "replaceLocalhostReferences", Path.class, Map.class, String.class, String.class);
            method.setAccessible(true);
            int replacedCount = (int) method.invoke(dependencyProvisionService, tempDir, Map.of(), rdsEndpoint, redisEndpoint);

            assertTrue(replacedCount > 0);

            String updatedContent = Files.readString(propFile);
            assertFalse(updatedContent.contains("localhost:3306"));
            assertTrue(updatedContent.contains(rdsEndpoint));
            assertTrue(updatedContent.contains("devdb"));
        } catch (Exception e) {
            fail("Reflection call failed: " + e.getMessage());
        }
    }

    @Test
    void testDependencyProvisionAwsKeyNeutralization(@TempDir Path tempDir) throws IOException {
        Path propFile = tempDir.resolve("application.properties");
        String originalContent = "cloud.aws.credentials.access-key=AKIAIOSFODNN7EXAMPLE\n" +
                                 "cloud.aws.credentials.secret-key=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
        Files.writeString(propFile, originalContent);

        try {
            java.lang.reflect.Method method = DependencyProvisionService.class.getDeclaredMethod(
                    "neutralizeHardcodedAwsCredentials", Path.class);
            method.setAccessible(true);
            int neutralizedCount = (int) method.invoke(dependencyProvisionService, tempDir);

            assertTrue(neutralizedCount > 0);

            String updatedContent = Files.readString(propFile);
            assertTrue(updatedContent.contains("# cloud.aws.credentials.access-key=AKIAIOSFODNN7EXAMPLE"));
            assertTrue(updatedContent.contains("cloud.aws.credentials.instance-profile=true"));
            assertTrue(updatedContent.contains("cloud.aws.credentials.use-default-aws-credentials-chain=true"));
        } catch (Exception e) {
            fail("Reflection call failed: " + e.getMessage());
        }
    }

    @Test
    void testDependencyProvisionCorsNeutralization(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("WebSecurityConfig.java");
        String originalContent = "public class WebSecurityConfig {\n" +
                                 "    public void cors() {\n" +
                                 "        config.setAllowedOrigins(List.of(\"http://localhost:3000\", \"http://localhost:8080\"));\n" +
                                 "    }\n" +
                                 "}";
        Files.writeString(javaFile, originalContent);

        try {
            java.lang.reflect.Method method = DependencyProvisionService.class.getDeclaredMethod(
                    "neutralizeCorsSettings", Path.class);
            method.setAccessible(true);
            int neutralizedCount = (int) method.invoke(dependencyProvisionService, tempDir);

            assertTrue(neutralizedCount > 0);

            String updatedContent = Files.readString(javaFile);
            assertTrue(updatedContent.contains("setAllowedOrigins(List.of(\"*\"))"));
        } catch (Exception e) {
            fail("Reflection call failed: " + e.getMessage());
        }
    }

    @Test
    void testHealthCheckClassification() {
        HealthCheckService.HealthResult refused = new HealthCheckService.HealthResult(
                false, 0, 100, "Connection refused", HealthCheckService.FailureCategory.CONNECTION_REFUSED);
        HealthCheckService.HealthResult timeout = new HealthCheckService.HealthResult(
                false, 0, 5000, "Timeout", HealthCheckService.FailureCategory.TIMEOUT);
        HealthCheckService.HealthResult serverError = new HealthCheckService.HealthResult(
                false, 500, 150, "Internal Server Error", HealthCheckService.FailureCategory.SERVER_ERROR);

        String refusedDiag = healthCheckService.classifyFailure(refused);
        String timeoutDiag = healthCheckService.classifyFailure(timeout);
        String serverDiag = healthCheckService.classifyFailure(serverError);

        assertTrue(refusedDiag.contains("listening on the expected port"));
        assertTrue(timeoutDiag.contains("running but the application is not responding"));
        assertTrue(serverDiag.contains("returned HTTP 500"));
    }

    @Test
    void testStartupResiliencePatcher(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("S3Service.java");
        String originalContent = "package com.example;\n" +
                                 "import jakarta.annotation.PostConstruct;\n" +
                                 "public class S3Service {\n" +
                                 "    private S3Client s3Client;\n" +
                                 "    @PostConstruct\n" +
                                 "    public void init() {\n" +
                                 "        s3Client.listBuckets();\n" +
                                 "    }\n" +
                                 "}";
        Files.writeString(javaFile, originalContent);

        int patchedFiles = startupResilienceService.patchDangerousInitMethods(tempDir, msg -> {});
        assertEquals(1, patchedFiles);

        String updatedContent = Files.readString(javaFile);
        assertTrue(updatedContent.contains("try {"));
        assertTrue(updatedContent.contains("} catch (Exception __resilience_ex) {"));
        assertTrue(updatedContent.contains("System.err.println(\"⚠️ [Autopilot] @PostConstruct init failed (non-fatal): \" + __resilience_ex.getMessage());"));
    }

    @Test
    void testDeploymentLogSanitization() {
        String rdsPassMsg = "Database connection established with password AP1234567890abcdef!";
        String sanitizedRdsMsg = com.autopilot.service.log.DeploymentLogService.sanitizeMessage(rdsPassMsg);
        assertTrue(sanitizedRdsMsg.contains("[REDACTED_PASSWORD]"));
        assertFalse(sanitizedRdsMsg.contains("AP1234567890abcdef!"));

        String connStrMsg = "Connecting to mysql://admin:SecretPass123@autopilot-db.rds.amazonaws.com:3306/autopilotdb";
        String sanitizedConnStrMsg = com.autopilot.service.log.DeploymentLogService.sanitizeMessage(connStrMsg);
        assertTrue(sanitizedConnStrMsg.contains("[REDACTED_PASSWORD]"));
        assertFalse(sanitizedConnStrMsg.contains("SecretPass123"));

        String awsKeyMsg = "Exporting aws_access_key_id='AKIAIOSFODNN7EXAMPLE' and aws_secret_access_key='wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'";
        String sanitizedAwsKeyMsg = com.autopilot.service.log.DeploymentLogService.sanitizeMessage(awsKeyMsg);
        assertTrue(sanitizedAwsKeyMsg.contains("aws_access_key_id=[REDACTED]"));
        assertTrue(sanitizedAwsKeyMsg.contains("aws_secret_access_key=[REDACTED]"));
        assertFalse(sanitizedAwsKeyMsg.contains("AKIAIOSFODNN7EXAMPLE"));
        assertFalse(sanitizedAwsKeyMsg.contains("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"));
    }

    @Test
    void testDynamicPluginDiscovery() {
        FrameworkDetector customPlugin = new FrameworkDetector() {
            @Override
            public boolean matches(Path workspace, List<String> relativeFiles) {
                return relativeFiles.contains("custom-framework-marker.txt");
            }
            @Override
            public FrameworkMetadata detect(Path workspace, List<String> relativeFiles) {
                return FrameworkMetadata.builder()
                        .name("custom-service")
                        .frameworkType(FrameworkType.GENERIC)
                        .runtimeType(com.autopilot.analyzer.model.RuntimeType.GENERIC)
                        .packageManager(com.autopilot.analyzer.model.PackageManager.NONE)
                        .buildCommand("echo build")
                        .startCommand("echo start")
                        .port(9999)
                        .language("java")
                        .build();
            }
        };

        frameworkRegistry.registerDetector(customPlugin);

        try {
            FrameworkDetector detector = detectorFactory.getDetector(Path.of(""), List.of("custom-framework-marker.txt"));
            assertEquals(customPlugin, detector);
            FrameworkMetadata metadata = detector.detect(Path.of(""), List.of("custom-framework-marker.txt"));
            assertEquals("custom-service", metadata.getName());
        } finally {
            frameworkRegistry.getDetectors().remove(customPlugin);
        }
    }

    @Autowired
    private com.autopilot.service.deployment.DockerImageValidatorService imageValidatorService;

    @Autowired
    private com.autopilot.service.deployment.DeploymentPipelineService deploymentPipelineService;

    @Test
    void testDockerImageValidation() {
        com.autopilot.analyzer.model.ServiceConfig javaService = new com.autopilot.analyzer.model.ServiceConfig();
        javaService.setName("java-backend");
        javaService.setLanguage("java");
        javaService.setFramework("springboot");

        com.autopilot.service.deployment.DockerImageValidatorService.ImageValidationResult result =
                imageValidatorService.validateImage("non-existent-image-tag", javaService);
        assertTrue(result.valid);
    }

    @Test
    void testDeploymentManifestGeneration() throws Exception {
        com.autopilot.entity.Deployment deployment = new com.autopilot.entity.Deployment();
        deployment.setId("test-deployment-id-123");
        deployment.setStrategyUsed("springboot");
        deployment.setRuntimeVersion("17");
        deployment.setImageUri("123456789012.dkr.ecr.us-east-1.amazonaws.com/test-repo:latest");
        deployment.setRdsEndpoint("mydb.c123456789.us-east-1.rds.amazonaws.com:3306");
        deployment.setBasePath("/app-test1234");

        com.autopilot.analyzer.model.ServiceConfig sc = new com.autopilot.analyzer.model.ServiceConfig();
        sc.setName("backend-service");
        sc.setFramework("springboot");
        sc.setLanguage("java");
        sc.setRuntimeVersion("17");
        sc.setPath("/tmp/workspace");
        sc.setPort(8080);

        List<com.autopilot.dto.DeployedService> deployed = List.of(
                new com.autopilot.dto.DeployedService("backend-service", "springboot", "java", "/tmp/workspace", 8080, 32123, "/app-test1234", "img", "backend", "mvn package", "java -jar", "17")
        );

        java.util.Map<String, String> envs = new java.util.HashMap<>();
        envs.put("DATABASE_PASSWORD", "SuperSecretPassword123!");
        envs.put("AWS_SECRET_ACCESS_KEY", "AKIASECRETKEY123");
        envs.put("PUBLIC_API_URL", "https://api.example.com");

        com.autopilot.service.deployment.DependencyProvisionService.ProvisionResult prov =
                new com.autopilot.service.deployment.DependencyProvisionService.ProvisionResult(
                        envs,
                        List.of("-e DB_PASS=..."),
                        "mydb.rds.amazonaws.com",
                        "redis://localhost:6379",
                        "arn:aws:secrets...",
                        "sg-12345678",
                        List.of("redis"),
                        List.of(),
                        List.of()
                );

        deploymentPipelineService.saveDeploymentManifest(deployment, List.of(sc), deployed, prov, "SUCCESS");

        String manifestJson = deployment.getDeployedServicesJson();
        assertNotNull(manifestJson);

        com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(manifestJson);
        assertEquals("springboot", rootNode.get("framework").asText());
        assertEquals("SUCCESS", rootNode.get("deploymentStatus").asText());
        assertEquals("/app-test1234/health", rootNode.get("healthEndpoint").asText());

        com.fasterxml.jackson.databind.JsonNode envsNode = rootNode.get("environmentVariables");
        assertEquals("[REDACTED_PASSWORD]", envsNode.get("DATABASE_PASSWORD").asText());
        assertEquals("[REDACTED_PASSWORD]", envsNode.get("AWS_SECRET_ACCESS_KEY").asText());
        assertEquals("https://api.example.com", envsNode.get("PUBLIC_API_URL").asText());
    }

    @Autowired
    private com.autopilot.analyzer.RepoAnalyzerService repoAnalyzerService;

    @Test
    void testRepositoryRootNoApplication(@TempDir Path tempDir) throws Exception {
        // Repository root with no application files at all falls back to generic app
        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(1, res.getServices().size());
        assertEquals("generic", res.getServices().get(0).getFramework());
    }

    @Test
    void testRepositoryRootContainingApplication(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("package.json"), "{\"dependencies\": {\"react\": \"^18.0.0\", \"vite\": \"^4.0.0\"}}");
        Files.writeString(tempDir.resolve("index.html"), "<html></html>");
        
        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(1, res.getServices().size());
        assertEquals("react_vite", res.getServices().get(0).getFramework());
        assertEquals(tempDir.toAbsolutePath().normalize().toString(), res.getServices().get(0).getServiceRoot());
    }

    @Test
    void testFrontendInFrontend(@TempDir Path tempDir) throws Exception {
        Path feDir = tempDir.resolve("frontend");
        Files.createDirectories(feDir);
        Files.writeString(feDir.resolve("package.json"), "{\"dependencies\": {\"react\": \"^18.0.0\", \"vite\": \"^4.0.0\"}}");
        Files.writeString(feDir.resolve("index.html"), "<html></html>");
        
        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(1, res.getServices().size());
        assertEquals("frontend", res.getServices().get(0).getServiceId());
        assertEquals(feDir.toAbsolutePath().normalize().toString(), res.getServices().get(0).getServiceRoot());
    }

    @Test
    void testFrontendInAppsWeb(@TempDir Path tempDir) throws Exception {
        Path feDir = tempDir.resolve("apps/web");
        Files.createDirectories(feDir);
        Files.writeString(feDir.resolve("package.json"), "{\"dependencies\": {\"react\": \"^18.0.0\", \"vite\": \"^4.0.0\"}}");
        Files.writeString(feDir.resolve("index.html"), "<html></html>");
        
        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(1, res.getServices().size());
        assertEquals("web", res.getServices().get(0).getServiceId());
        assertEquals(feDir.toAbsolutePath().normalize().toString(), res.getServices().get(0).getServiceRoot());
    }

    @Test
    void testBackendInServicesApi(@TempDir Path tempDir) throws Exception {
        Path beDir = tempDir.resolve("services/api");
        Files.createDirectories(beDir);
        Files.writeString(beDir.resolve("pom.xml"), "<project><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency></dependencies></project>");
        
        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(1, res.getServices().size());
        assertEquals("api", res.getServices().get(0).getServiceId());
        assertEquals(beDir.toAbsolutePath().normalize().toString(), res.getServices().get(0).getServiceRoot());
    }

    @Test
    void testNestedFrontendBackend(@TempDir Path tempDir) throws Exception {
        Path fe = tempDir.resolve("frontend");
        Path be = tempDir.resolve("backend");
        Files.createDirectories(fe);
        Files.createDirectories(be);
        Files.writeString(fe.resolve("package.json"), "{\"dependencies\": {\"react\": \"18.0.0\", \"vite\": \"4.0.0\"}}");
        Files.writeString(fe.resolve("index.html"), "<html></html>");
        Files.writeString(be.resolve("pom.xml"), "<project><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency></dependencies></project>");

        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(2, res.getServices().size());
    }

    @Test
    void testTurborepo(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("package.json"), "{\"private\": true, \"workspaces\": [\"apps/*\"]}");
        Path fe = tempDir.resolve("apps/web");
        Path api = tempDir.resolve("apps/api");
        Files.createDirectories(fe);
        Files.createDirectories(api);
        Files.writeString(fe.resolve("package.json"), "{\"dependencies\": {\"react\": \"18.0.0\", \"vite\": \"4.0.0\"}}");
        Files.writeString(fe.resolve("index.html"), "<html></html>");
        Files.writeString(api.resolve("package.json"), "{\"dependencies\": {\"express\": \"4.18.0\"}}");

        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(2, res.getServices().size());
        List<String> names = res.getServices().stream().map(s -> s.getServiceId()).collect(java.util.stream.Collectors.toList());
        assertTrue(names.contains("web"));
        assertTrue(names.contains("api"));
    }

    @Test
    void testNx(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("nx.json"), "{}");
        Files.writeString(tempDir.resolve("package.json"), "{\"private\": true}");
        Path fe = tempDir.resolve("apps/web");
        Path api = tempDir.resolve("apps/api");
        Files.createDirectories(fe);
        Files.createDirectories(api);
        Files.writeString(fe.resolve("package.json"), "{\"dependencies\": {\"react\": \"18.0.0\", \"vite\": \"4.0.0\"}}");
        Files.writeString(fe.resolve("index.html"), "<html></html>");
        Files.writeString(api.resolve("package.json"), "{\"dependencies\": {\"express\": \"4.18.0\"}}");

        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(2, res.getServices().size());
    }

    @Test
    void testPnpmWorkspace(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pnpm-workspace.yaml"), "packages:\n  - 'apps/*'\n");
        Path fe = tempDir.resolve("apps/web");
        Path api = tempDir.resolve("apps/api");
        Files.createDirectories(fe);
        Files.createDirectories(api);
        Files.writeString(fe.resolve("package.json"), "{\"dependencies\": {\"react\": \"18.0.0\", \"vite\": \"4.0.0\"}}");
        Files.writeString(fe.resolve("index.html"), "<html></html>");
        Files.writeString(api.resolve("package.json"), "{\"dependencies\": {\"express\": \"4.18.0\"}}");

        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(2, res.getServices().size());
    }

    @Test
    void testMavenMultiModule(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project><packaging>pom</packaging><modules><module>apps/api</module></modules></project>");
        Path api = tempDir.resolve("apps/api");
        Files.createDirectories(api);
        Files.writeString(api.resolve("pom.xml"), "<project><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency></dependencies></project>");

        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(1, res.getServices().size());
        assertEquals("api", res.getServices().get(0).getServiceId());
    }

    @Test
    void testGradleMultiModule(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("settings.gradle"), "include 'apps:api'");
        Path api = tempDir.resolve("apps/api");
        Files.createDirectories(api);
        Files.writeString(api.resolve("build.gradle"), "plugins { id 'org.springframework.boot' version '3.2.0' }");

        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(1, res.getServices().size());
        assertEquals("api", res.getServices().get(0).getServiceId());
    }

    @Test
    void testMultiplePackageJson(@TempDir Path tempDir) throws Exception {
        Path fe1 = tempDir.resolve("fe1");
        Path fe2 = tempDir.resolve("fe2");
        Files.createDirectories(fe1);
        Files.createDirectories(fe2);
        Files.writeString(fe1.resolve("package.json"), "{\"dependencies\": {\"react\": \"18.0.0\", \"vite\": \"4.0.0\"}}");
        Files.writeString(fe1.resolve("index.html"), "<html></html>");
        Files.writeString(fe2.resolve("package.json"), "{\"dependencies\": {\"react\": \"18.0.0\", \"vite\": \"4.0.0\"}}");
        Files.writeString(fe2.resolve("index.html"), "<html></html>");

        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(2, res.getServices().size());
    }

    @Test
    void testMultiplePomXml(@TempDir Path tempDir) throws Exception {
        Path be1 = tempDir.resolve("be1");
        Path be2 = tempDir.resolve("be2");
        Files.createDirectories(be1);
        Files.createDirectories(be2);
        Files.writeString(be1.resolve("pom.xml"), "<project><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency></dependencies></project>");
        Files.writeString(be2.resolve("pom.xml"), "<project><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency></dependencies></project>");

        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(2, res.getServices().size());
    }

    @Autowired
    private com.autopilot.service.deployment.DockerBuilder dockerBuilder;

    @Test
    void testDockerBuildContextValidation(@TempDir Path tempDir) {
        com.autopilot.analyzer.model.ServiceConfig invalidService = new com.autopilot.analyzer.model.ServiceConfig();
        invalidService.setName("invalid-node-app");
        invalidService.setFramework("react_vite");
        invalidService.setPath(tempDir.toAbsolutePath().toString());

        // Call buildSafeSuffix on directory lacking package.json
        com.autopilot.service.deployment.DockerBuilder.BuildResult res = 
                dockerBuilder.buildSafeSuffix(invalidService, "test-deploy", "-svc0");
        
        assertFalse(res.success);
        assertEquals("INVALID_BUILD_CONTEXT", res.errorCategory);
        assertTrue(res.logs.get(0).contains("Expected manifest file package.json is missing"));
    }

    @Autowired
    private com.autopilot.service.deployment.validation.BuildContextValidator buildContextValidator;

    @Test
    void testValidationFrontendOnly(@TempDir Path tempDir) throws Exception {
        Path fe = tempDir.resolve("fe");
        Files.createDirectories(fe);
        Files.writeString(fe.resolve("package.json"), "{}");

        com.autopilot.analyzer.model.ServiceConfig service = new com.autopilot.analyzer.model.ServiceConfig();
        service.setServiceId("frontend");
        service.setFramework("react_vite");
        service.setServiceRoot(fe.toAbsolutePath().toString());

        // Validate should succeed without errors
        buildContextValidator.validate(service);
    }

    @Test
    void testValidationBackendOnly(@TempDir Path tempDir) throws Exception {
        Path be = tempDir.resolve("be");
        Files.createDirectories(be);
        Files.writeString(be.resolve("pom.xml"), "<project></project>");

        com.autopilot.analyzer.model.ServiceConfig service = new com.autopilot.analyzer.model.ServiceConfig();
        service.setServiceId("backend");
        service.setFramework("spring_boot");
        service.setServiceRoot(be.toAbsolutePath().toString());

        buildContextValidator.validate(service);
    }

    @Test
    void testValidationFrontendAndBackend(@TempDir Path tempDir) throws Exception {
        Path fe = tempDir.resolve("fe");
        Path be = tempDir.resolve("be");
        Files.createDirectories(fe);
        Files.createDirectories(be);
        Files.writeString(fe.resolve("package.json"), "{}");
        Files.writeString(be.resolve("pom.xml"), "<project></project>");

        com.autopilot.analyzer.model.ServiceConfig service1 = new com.autopilot.analyzer.model.ServiceConfig();
        service1.setServiceId("frontend");
        service1.setFramework("react_vite");
        service1.setServiceRoot(fe.toAbsolutePath().toString());

        com.autopilot.analyzer.model.ServiceConfig service2 = new com.autopilot.analyzer.model.ServiceConfig();
        service2.setServiceId("backend");
        service2.setFramework("spring_boot");
        service2.setServiceRoot(be.toAbsolutePath().toString());

        // Both must validate successfully and independently
        buildContextValidator.validate(service1);
        buildContextValidator.validate(service2);
    }

    @Test
    void testValidationBackendAndFrontend(@TempDir Path tempDir) throws Exception {
        Path fe = tempDir.resolve("fe");
        Path be = tempDir.resolve("be");
        Files.createDirectories(fe);
        Files.createDirectories(be);
        Files.writeString(fe.resolve("package.json"), "{}");
        Files.writeString(be.resolve("pom.xml"), "<project></project>");

        com.autopilot.analyzer.model.ServiceConfig service1 = new com.autopilot.analyzer.model.ServiceConfig();
        service1.setServiceId("backend");
        service1.setFramework("spring_boot");
        service1.setServiceRoot(be.toAbsolutePath().toString());

        com.autopilot.analyzer.model.ServiceConfig service2 = new com.autopilot.analyzer.model.ServiceConfig();
        service2.setServiceId("frontend");
        service2.setFramework("react_vite");
        service2.setServiceRoot(fe.toAbsolutePath().toString());

        buildContextValidator.validate(service1);
        buildContextValidator.validate(service2);
    }

    @Test
    void testValidationTwoFrontends(@TempDir Path tempDir) throws Exception {
        Path fe1 = tempDir.resolve("fe1");
        Path fe2 = tempDir.resolve("fe2");
        Files.createDirectories(fe1);
        Files.createDirectories(fe2);
        Files.writeString(fe1.resolve("package.json"), "{}");
        Files.writeString(fe2.resolve("package.json"), "{}");

        com.autopilot.analyzer.model.ServiceConfig service1 = new com.autopilot.analyzer.model.ServiceConfig();
        service1.setServiceId("fe1");
        service1.setFramework("react_vite");
        service1.setServiceRoot(fe1.toAbsolutePath().toString());

        com.autopilot.analyzer.model.ServiceConfig service2 = new com.autopilot.analyzer.model.ServiceConfig();
        service2.setServiceId("fe2");
        service2.setFramework("nextjs");
        service2.setServiceRoot(fe2.toAbsolutePath().toString());

        buildContextValidator.validate(service1);
        buildContextValidator.validate(service2);
    }

    @Test
    void testValidationTwoBackends(@TempDir Path tempDir) throws Exception {
        Path be1 = tempDir.resolve("be1");
        Path be2 = tempDir.resolve("be2");
        Files.createDirectories(be1);
        Files.createDirectories(be2);
        Files.writeString(be1.resolve("pom.xml"), "<project></project>");
        Files.writeString(be2.resolve("build.gradle"), "");

        com.autopilot.analyzer.model.ServiceConfig service1 = new com.autopilot.analyzer.model.ServiceConfig();
        service1.setServiceId("be1");
        service1.setFramework("spring_boot");
        service1.setServiceRoot(be1.toAbsolutePath().toString());

        com.autopilot.analyzer.model.ServiceConfig service2 = new com.autopilot.analyzer.model.ServiceConfig();
        service2.setServiceId("be2");
        service2.setFramework("spring_boot");
        service2.setServiceRoot(be2.toAbsolutePath().toString());

        buildContextValidator.validate(service1);
        buildContextValidator.validate(service2);
    }

    @Test
    void testValidationThreeMixedServices(@TempDir Path tempDir) throws Exception {
        Path fe = tempDir.resolve("fe");
        Path be = tempDir.resolve("be");
        Path py = tempDir.resolve("py");
        Files.createDirectories(fe);
        Files.createDirectories(be);
        Files.createDirectories(py);
        Files.writeString(fe.resolve("package.json"), "{}");
        Files.writeString(be.resolve("pom.xml"), "<project></project>");
        Files.writeString(py.resolve("requirements.txt"), "");

        com.autopilot.analyzer.model.ServiceConfig service1 = new com.autopilot.analyzer.model.ServiceConfig();
        service1.setServiceId("fe");
        service1.setFramework("react_vite");
        service1.setServiceRoot(fe.toAbsolutePath().toString());

        com.autopilot.analyzer.model.ServiceConfig service2 = new com.autopilot.analyzer.model.ServiceConfig();
        service2.setServiceId("be");
        service2.setFramework("spring_boot");
        service2.setServiceRoot(be.toAbsolutePath().toString());

        com.autopilot.analyzer.model.ServiceConfig service3 = new com.autopilot.analyzer.model.ServiceConfig();
        service3.setServiceId("py");
        service3.setFramework("fastapi");
        service3.setLanguage("python");
        service3.setServiceRoot(py.toAbsolutePath().toString());

        buildContextValidator.validate(service1);
        buildContextValidator.validate(service2);
        buildContextValidator.validate(service3);
    }

    @Test
    void testParallelValidationAndNoStateLeakage(@TempDir Path tempDir) throws Exception {
        Path fe = tempDir.resolve("fe");
        Path be = tempDir.resolve("be");
        Files.createDirectories(fe);
        Files.createDirectories(be);
        Files.writeString(fe.resolve("package.json"), "{}");
        Files.writeString(be.resolve("pom.xml"), "<project></project>");

        com.autopilot.analyzer.model.ServiceConfig service1 = new com.autopilot.analyzer.model.ServiceConfig();
        service1.setServiceId("fe");
        service1.setFramework("react_vite");
        service1.setServiceRoot(fe.toAbsolutePath().toString());

        com.autopilot.analyzer.model.ServiceConfig service2 = new com.autopilot.analyzer.model.ServiceConfig();
        service2.setServiceId("be");
        service2.setFramework("spring_boot");
        service2.setServiceRoot(be.toAbsolutePath().toString());

        // Validate multiple times in parallel to ensure no thread local or registry state leakage
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> buildContextValidator.validate(service1)));
                futures.add(executor.submit(() -> buildContextValidator.validate(service2)));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                f.get(); // check if any threw exceptions
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testNestedMonorepoAnalysis(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency></dependencies></project>");
        Path src = tempDir.resolve("src/main/java/com/app");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), "package com.app; public class App {}");

        Path fe = tempDir.resolve("frontend");
        Files.createDirectories(fe);
        Files.writeString(fe.resolve("package.json"), "{\"dependencies\": {\"react\": \"18.0.0\", \"vite\": \"4.0.0\"}}");
        Files.writeString(fe.resolve("index.html"), "<html></html>");

        com.autopilot.analyzer.model.RepoAnalysisResult res = repoAnalyzerService.analyzeWorkspace(tempDir);
        assertEquals(2, res.getServices().size());
        
        for (com.autopilot.analyzer.model.ServiceConfig service : res.getServices()) {
            assertDoesNotThrow(() -> buildContextValidator.validate(service));
        }
    }
}

