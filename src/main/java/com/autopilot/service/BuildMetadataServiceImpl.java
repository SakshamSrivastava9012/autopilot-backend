package com.autopilot.service;

import org.springframework.stereotype.Service;
import java.io.File;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

@Service
public class BuildMetadataServiceImpl implements BuildMetadataService {

    private final String version;
    private final String gitCommit;
    private final String buildTimestamp;
    private final long startTime;
    private final String pid;
    private final String runningJarChecksum;

    public BuildMetadataServiceImpl() {
        this.startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        this.pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

        // Load build-info
        Properties buildInfo = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("META-INF/build-info.properties")) {
            if (is != null) {
                buildInfo.load(is);
            }
        } catch (Exception ignored) {}

        this.version = buildInfo.getProperty("build.version", "0.0.1-SNAPSHOT");
        this.buildTimestamp = buildInfo.getProperty("build.time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date(startTime)));

        // Get git commit dynamically
        String commit = "unknown";
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD").start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    commit = line.trim();
                }
            }
        } catch (Exception ignored) {}
        this.gitCommit = commit;

        // Calculate and cache the running JAR checksum at startup
        this.runningJarChecksum = calculateChecksumAtStartup();
    }

    @Override
    public String getVersion() { return version; }
    @Override
    public String getGitCommit() { return gitCommit; }
    @Override
    public String getBuildTimestamp() { return buildTimestamp; }
    @Override
    public long getStartTime() { return startTime; }
    @Override
    public String getPid() { return pid; }
    @Override
    public String getRunningJarChecksum() { return runningJarChecksum; }

    @Override
    public String getRunningJarPath() {
        try {
            java.security.ProtectionDomain domain = getClass().getProtectionDomain();
            java.security.CodeSource codeSource = domain.getCodeSource();
            if (codeSource == null) return null;
            java.net.URL location = codeSource.getLocation();
            String path = URLDecoder.decode(location.getPath(), StandardCharsets.UTF_8);
            if (path.startsWith("nested:")) {
                path = path.substring(7);
            }
            if (path.contains(".jar/")) {
                path = path.substring(0, path.indexOf(".jar/") + 4);
            }
            if (path.contains(".jar!")) {
                path = path.substring(0, path.indexOf(".jar!") + 4);
            }
            if (path.startsWith("file:")) {
                path = path.substring(5);
            }
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    private String calculateChecksumAtStartup() {
        String jarPath = getRunningJarPath();
        if (jarPath == null) return "N/A";
        File file = new File(jarPath);
        if (!file.exists() || file.isDirectory()) {
            return "N/A (unpacked or running in IDE)";
        }
        try (InputStream fis = new java.io.FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
