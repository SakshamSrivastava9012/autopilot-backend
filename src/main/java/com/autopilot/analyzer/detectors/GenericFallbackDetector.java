package com.autopilot.analyzer.detectors;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.FrameworkType;
import com.autopilot.analyzer.model.PackageManager;
import com.autopilot.analyzer.model.RuntimeType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class GenericFallbackDetector implements FrameworkDetector {
    @Override
    public boolean matches(Path workspace, List<String> files) {
        return true; // final fallback
    }

    @Override
    public FrameworkMetadata detect(Path workspace, List<String> files) {
        // Try to guess language from file extensions
        long javaCount = files.stream().filter(f -> f.endsWith(".java")).count();
        long jsCount = files.stream().filter(f -> f.endsWith(".js") || f.endsWith(".ts")).count();
        long pyCount = files.stream().filter(f -> f.endsWith(".py")).count();
        long goCount = files.stream().filter(f -> f.endsWith(".go")).count();

        String lang = "unknown";
        PackageManager pm = PackageManager.NONE;
        String buildCmd = "echo 'Unknown project - skipping build'";
        String startCmd = "ls -la";
        String rtVersion = "latest";
        RuntimeType rtType = RuntimeType.GENERIC;

        if (javaCount > jsCount && javaCount > pyCount && javaCount > goCount) {
            lang = "java";
            pm = PackageManager.MAVEN;
            buildCmd = "mvn clean package -DskipTests";
            startCmd = "java -jar target/*.jar";
            rtVersion = "17";
            rtType = RuntimeType.JAVA_JAR;
        } else if (jsCount > pyCount && jsCount > goCount) {
            lang = "javascript";
            pm = PackageManager.NPM;
            buildCmd = "npm install && npm run build";
            startCmd = "npm start";
            rtVersion = "20";
            rtType = RuntimeType.NODE_SERVER;
        } else if (pyCount > goCount) {
            lang = "python";
            pm = PackageManager.PIP;
            buildCmd = "pip install -r requirements.txt || true";
            startCmd = "python app.py";
            rtVersion = "3.10";
            rtType = RuntimeType.PYTHON;
        } else if (goCount > 0) {
            lang = "go";
            pm = PackageManager.GO;
            buildCmd = "go build -o server .";
            startCmd = "./server";
            rtVersion = "1.22";
            rtType = RuntimeType.GO_BINARY;
        }

        return FrameworkMetadata.builder()
                .name("generic-app")
                .frameworkType(FrameworkType.GENERIC)
                .runtimeType(rtType)
                .packageManager(pm)
                .buildCommand(buildCmd)
                .startCommand(startCmd)
                .outputDirectory(".")
                .port(8080)
                .healthCheckPath("/")
                .language(lang)
                .defaultRuntimeVersion(rtVersion)
                .build();
    }
}
