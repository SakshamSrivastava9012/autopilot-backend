package com.autopilot.service.deployment.validation;

import com.autopilot.dto.DeployedService;
import com.autopilot.dto.DeploymentManifest;
import com.autopilot.dto.AssetManifestEntry;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CapabilityAssetVerifier {

    public List<String> verifyAssets(DeployedService ds, String publicIp, String accessUrl, DeploymentManifest manifest) {
        List<String> failures = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        System.out.println("🕸️ Starting Recursive Asset Graph Validation for service: " + ds.getName() + " at " + accessUrl);

        // 1. Run the recursive asset crawler starting from the access URL
        crawlAndVerify(accessUrl, ds.getBasePath(), visited, failures);

        // 2. Cross-verify with the Asset Manifest to ensure all recorded files are queryable
        if (manifest != null && manifest.getAssets() != null) {
            for (AssetManifestEntry asset : manifest.getAssets()) {
                String assetUrl = "http://" + publicIp + asset.getPublicUrl();
                assetUrl = assetUrl.replaceAll("(?<!:)/+", "/");

                if (!visited.contains(assetUrl)) {
                    try {
                        HttpURLConnection assetConn = (HttpURLConnection) URI.create(assetUrl).toURL().openConnection();
                        assetConn.setRequestMethod("GET");
                        assetConn.setConnectTimeout(3000);
                        int assetCode = assetConn.getResponseCode();

                        if (assetCode != 200 && assetCode != 304) {
                            failures.add("Asset " + asset.getLogicalPath() + " at " + assetUrl + " returned status " + assetCode);
                            System.out.println("❌ Manifest asset verification FAILED: " + asset.getLogicalPath() + " -> " + assetCode);
                        } else {
                            visited.add(assetUrl);
                            System.out.println("✓ Manifest asset verified: " + asset.getLogicalPath() + " -> " + assetCode);
                        }
                    } catch (Exception e) {
                        failures.add("Failed to verify asset " + asset.getLogicalPath() + ": " + e.getMessage());
                    }
                }
            }
        }

        System.out.println("🕸️ Recursive Asset Graph Validation complete. Visited: " + visited.size() + " resources. Failures: " + failures.size());
        return failures;
    }

    private void crawlAndVerify(String url, String basePath, Set<String> visited, List<String> failures) {
        if (visited.contains(url)) {
            return;
        }
        visited.add(url);

        try {
            URI uri = URI.create(url);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();

            if (code != 200 && code != 304) {
                failures.add("Failed to load resource: " + url + " (Status: " + code + ")");
                return;
            }

            String contentType = conn.getContentType();
            if (contentType == null) contentType = "";

            if (contentType.contains("text/html") || url.endsWith(".html") || url.endsWith("/")) {
                String html;
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()))) {
                    html = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
                }
                Set<String> refs = extractHtmlReferences(html);
                for (String ref : refs) {
                    try {
                        String resolved = uri.resolve(ref.trim()).toString();
                        if (resolved.contains(basePath)) {
                            crawlAndVerify(resolved, basePath, visited, failures);
                        }
                    } catch (Exception ignored) {}
                }
            } else if (contentType.contains("text/css") || url.endsWith(".css")) {
                String css;
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()))) {
                    css = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
                }
                Set<String> refs = extractUrlsFromCss(css);
                for (String ref : refs) {
                    try {
                        String resolved = uri.resolve(ref.trim()).toString();
                        if (resolved.contains(basePath)) {
                            crawlAndVerify(resolved, basePath, visited, failures);
                        }
                    } catch (Exception ignored) {}
                }
            } else if (contentType.contains("javascript") || url.endsWith(".js")) {
                String js;
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()))) {
                    js = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
                }
                Set<String> refs = extractUrlsFromJs(js);
                for (String ref : refs) {
                    try {
                        String resolved = uri.resolve(ref.trim()).toString();
                        if (resolved.contains(basePath)) {
                            crawlAndVerify(resolved, basePath, visited, failures);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            failures.add("Error verifying resource " + url + ": " + e.getMessage());
        }
    }

    private Set<String> extractHtmlReferences(String html) {
        Set<String> refs = new HashSet<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)(src|href)\\s*=\\s*['\"]([^'\"]+)['\"]");
        java.util.regex.Matcher m = p.matcher(html);
        while (m.find()) {
            String url = m.group(2);
            if (!url.startsWith("http") && !url.startsWith("//") && !url.startsWith("data:") && !url.startsWith("blob:")) {
                refs.add(url);
            }
        }
        return refs;
    }

    private Set<String> extractUrlsFromCss(String cssContent) {
        Set<String> urls = new HashSet<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("url\\s*\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)");
        java.util.regex.Matcher m = p.matcher(cssContent);
        while (m.find()) {
            urls.add(m.group(1));
        }
        return urls;
    }

    private Set<String> extractUrlsFromJs(String jsContent) {
        Set<String> urls = new HashSet<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("['\"]([^'\"]+\\.(js|css|png|jpg|jpeg|gif|svg|woff2|woff|ttf|json|wasm))['\"]");
        java.util.regex.Matcher m = p.matcher(jsContent);
        while (m.find()) {
            urls.add(m.group(1));
        }
        java.util.regex.Pattern workerPat = java.util.regex.Pattern.compile("new\\s+(Shared)?Worker\\s*\\(\\s*['\"]([^'\"]+)['\"]");
        java.util.regex.Matcher workerMat = workerPat.matcher(jsContent);
        while (workerMat.find()) {
            urls.add(workerMat.group(2));
        }
        return urls;
    }
}
