package com.autopilot.analyzer;

import com.autopilot.analyzer.detectors.FrameworkDetector;
import com.autopilot.analyzer.detectors.FrameworkDetectorFactory;
import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.ServiceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProjectClassifier {

    private final FrameworkDetectorFactory detectorFactory;

    public FrameworkMetadata classifyProject(Path subWorkspace, List<String> dirFiles) {
        FrameworkDetector detector = detectorFactory.getDetector(subWorkspace, dirFiles);
        return detector.detect(subWorkspace, dirFiles);
    }

    public String detectJavaVersion(Path workspace, List<String> files) {
        for (String file : files) {
            if (file.endsWith("pom.xml")) {
                try {
                    String content = Files.readString(workspace.resolve(file));
                    Matcher m1 = Pattern.compile("<java\\.version>(\\d+)</java\\.version>").matcher(content);
                    if (m1.find()) return m1.group(1);
                    Matcher m2 = Pattern.compile("<maven\\.compiler\\.source>(\\d+)</maven\\.compiler\\.source>").matcher(content);
                    if (m2.find()) return m2.group(1);
                    Matcher m3 = Pattern.compile("<release>(\\d+)</release>").matcher(content);
                    if (m3.find()) return m3.group(1);
                } catch (IOException ignored) {}
            }
        }
        for (String file : files) {
            if (file.endsWith("build.gradle")) {
                try {
                    String content = Files.readString(workspace.resolve(file));
                    Matcher m = Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?(\\d+)").matcher(content);
                    if (m.find()) return m.group(1);
                    Matcher m2 = Pattern.compile("JavaVersion\\.VERSION_(\\d+)").matcher(content);
                    if (m2.find()) return m2.group(1);
                } catch (IOException ignored) {}
            }
        }
        return "17"; // default fallback
    }
}
