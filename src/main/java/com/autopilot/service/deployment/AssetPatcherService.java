package com.autopilot.service.deployment;

import com.autopilot.dto.AssetManifestEntry;
import com.autopilot.dto.RuntimeManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class AssetPatcherService {

    public List<AssetManifestEntry> patchImage(String imageName, String basePath, String workspacePath, String deploymentId) {
        Path workspace = Path.of(workspacePath);
        Path tempDir = workspace.resolve("temp-patch-" + deploymentId + "-" + UUID.randomUUID().toString().substring(0, 8));
        String tempContainer = "temp-patch-container-" + deploymentId + "-" + UUID.randomUUID().toString().substring(0, 8);

        List<AssetManifestEntry> manifest = new ArrayList<>();
        try {
            Files.createDirectories(tempDir);

            // 1. Create temporary container
            runCmd("docker create --name " + tempContainer + " " + imageName);

            // 2. Try copying /usr/share/nginx/html or /app
            String containerDir = "/usr/share/nginx/html";
            boolean success = false;
            try {
                runCmd("docker cp " + tempContainer + ":/usr/share/nginx/html " + tempDir.toAbsolutePath());
                success = true;
            } catch (Exception e) {
                System.out.println("No /usr/share/nginx/html in container, trying /app...");
            }

            if (!success) {
                try {
                    runCmd("docker cp " + tempContainer + ":/app " + tempDir.toAbsolutePath());
                    containerDir = "/app";
                    success = true;
                } catch (Exception e) {
                    System.out.println("No /app in container, trying /var/www/html...");
                }
            }

            if (!success) {
                try {
                    runCmd("docker cp " + tempContainer + ":/var/www/html " + tempDir.toAbsolutePath());
                    containerDir = "/var/www/html";
                    success = true;
                } catch (Exception e) {
                    System.err.println("❌ Could not extract any standard app directory from container!");
                }
            }

            if (!success) {
                return manifest;
            }

            Path extractedAppDir = tempDir.resolve(containerDir.substring(containerDir.lastIndexOf("/") + 1));
            if (!Files.exists(extractedAppDir)) {
                try (var list = Files.list(tempDir)) {
                    extractedAppDir = list.findFirst().orElse(tempDir);
                }
            }

            System.out.println("Extracted app directory on host: " + extractedAppDir.toAbsolutePath());

            // 3. Scan for static assets
            List<Path> allFiles = new ArrayList<>();
            final Path appDirRef = extractedAppDir;
            Files.walk(extractedAppDir)
                    .filter(Files::isRegularFile)
                    .forEach(allFiles::add);

            Set<String> assetExtensions = Set.of(
                    "html", "css", "js", "ts", "jsx", "tsx", "png", "jpg", "jpeg",
                    "gif", "ico", "svg", "woff", "woff2", "ttf", "json", "webmanifest",
                    "txt", "xml", "lottie", "webp", "mp3", "mp4", "wav", "pdf"
            );

            for (Path file : allFiles) {
                String filename = file.getFileName().toString().toLowerCase();
                String relPath = appDirRef.toAbsolutePath().relativize(file.toAbsolutePath()).toString().replace("\\", "/");
                if (relPath.startsWith("/")) relPath = relPath.substring(1);

                // Skip non-public directories
                if (relPath.contains(".git/") || relPath.contains("node_modules/") ||
                        relPath.contains(".mvn/") || relPath.contains("target/") ||
                        relPath.contains("build/libs/") || relPath.contains(".next/server/")) {
                    continue;
                }

                String ext = "";
                int dotIdx = filename.lastIndexOf(".");
                if (dotIdx > 0) {
                    ext = filename.substring(dotIdx + 1);
                }

                if (assetExtensions.contains(ext)) {
                    // Compute logical path
                    String logicalPath = "/" + relPath;
                    if (relPath.startsWith("public/")) {
                        logicalPath = "/" + relPath.substring("public/".length());
                    } else if (relPath.startsWith("dist/")) {
                        logicalPath = "/" + relPath.substring("dist/".length());
                    } else if (relPath.startsWith("build/")) {
                        logicalPath = "/" + relPath.substring("build/".length());
                    } else if (relPath.startsWith("out/")) {
                        logicalPath = "/" + relPath.substring("out/".length());
                    } else if (relPath.startsWith(".next/static/")) {
                        logicalPath = "/_next/static/" + relPath.substring(".next/static/".length());
                    } else if (relPath.startsWith("wwwroot/")) {
                        logicalPath = "/" + relPath.substring("wwwroot/".length());
                    } else if (relPath.startsWith("public_html/")) {
                        logicalPath = "/" + relPath.substring("public_html/".length());
                    }

                    logicalPath = logicalPath.replace("//", "/");

                    String containerPath = containerDir + "/" + relPath;
                    containerPath = containerPath.replace("//", "/");

                    String publicUrl = basePath + logicalPath;
                    publicUrl = publicUrl.replace("//", "/");

                    boolean cacheable = !logicalPath.endsWith(".html") &&
                            !logicalPath.endsWith("manifest.json") &&
                            !logicalPath.endsWith("robots.txt");

                    manifest.add(AssetManifestEntry.builder()
                            .logicalPath(logicalPath)
                            .containerPath(containerPath)
                            .publicUrl(publicUrl)
                            .requiresPrefix(true)
                            .cacheable(cacheable)
                            .build());
                }
            }

            // 4. Inject runtime context and base tag into HTML files
            // No global string replacements or regex monkey-patching!
            for (Path file : allFiles) {
                String filename = file.getFileName().toString().toLowerCase();
                if (filename.endsWith(".html")) {
                    injectRuntimeContext(file, basePath, deploymentId);
                }
            }

            // Write asset-manifest.json to build folder
            Path manifestFile = extractedAppDir.resolve("asset-manifest.json");
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile.toFile(), manifest);
            System.out.println("Written asset-manifest.json to: " + manifestFile.toAbsolutePath());

            // Write runtime-manifest.json to build folder
            RuntimeManifest runtimeManifest = RuntimeManifest.builder()
                    .deploymentId(deploymentId)
                    .basePath(basePath)
                    .apiBasePath(basePath + "-api")
                    .assetPrefix(basePath)
                    .origin("")
                    .routes(new ArrayList<>())
                    .services(new ArrayList<>())
                    .assets(manifest)
                    .capabilities(List.of("SPA", "STATIC_ASSETS"))
                    .build();
            Path runtimeManifestFile = extractedAppDir.resolve("runtime-manifest.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(runtimeManifestFile.toFile(), runtimeManifest);
            System.out.println("Written runtime-manifest.json to: " + runtimeManifestFile.toAbsolutePath());

            // 5. Rebuild docker image
            Path dockerfile = tempDir.resolve("Dockerfile");
            String dockerfileContent = "FROM " + imageName + "\n" +
                    "COPY " + extractedAppDir.getFileName().toString() + " " + containerDir + "\n";
            Files.writeString(dockerfile, dockerfileContent);

            runCmd("docker build --no-cache -t " + imageName + " " + tempDir.toAbsolutePath().toString());
            System.out.println("✅ Rebuilt docker image " + imageName + " with cleanly injected runtime manifest and configuration.");

        } catch (Exception e) {
            System.err.println("❌ Error during asset discovery: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                runCmd("docker rm -f " + tempContainer);
            } catch (Exception ignored) {}

            try {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {}
        }

        return manifest;
    }

    private void injectRuntimeContext(Path htmlFile, String basePath, String did) {
        try {
            String content = Files.readString(htmlFile);

            // 1. Generate base tag if not present
            String baseTag = "<base href=\"" + basePath + "/\">";
            if (!content.contains("<base ")) {
                if (content.contains("<head>")) {
                    content = content.replace("<head>", "<head>\n    " + baseTag);
                } else if (content.contains("<html>")) {
                    content = content.replace("<html>", "<html>\n    <head>\n    " + baseTag + "\n    </head>\n");
                } else {
                    content = baseTag + "\n" + content;
                }
            }

            // 2. Generate Universal Runtime Context and Resolver
            String resolverScript = """
                <script id="autopilot-universal-resolver">
                (function() {
                  const BASE_PATH = "%s";
                  const API_PREFIX = "%s-api";
                  
                  window.__AUTOPILOT__ = {
                    BASE_PATH: BASE_PATH,
                    API_PREFIX: API_PREFIX,
                    ASSET_PREFIX: BASE_PATH,
                    ORIGIN: window.location.origin,
                    DEPLOYMENT_ID: "%s"
                  };
                  
                  function classify(url) {
                    if (!url || typeof url !== 'string') return 'EXTERNAL';
                    if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('//') || url.startsWith('blob:') || url.startsWith('data:') || url.startsWith('mailto:') || url.startsWith('tel:')) return 'EXTERNAL';
                    if (!url.startsWith('/')) return 'EXTERNAL';
                    
                    const isPrefixed = (u, p) => u.startsWith(p) && (u.length === p.length || u.charAt(p.length) === '/' || u.charAt(p.length) === '?' || u.charAt(p.length) === '#');
                    if (isPrefixed(url, BASE_PATH)) return 'ALREADY_PREFIXED';
                    if (isPrefixed(url, API_PREFIX)) return 'ALREADY_PREFIXED';
                    
                    const apiRegex = /^\\/(api|rest|graphql|rpc|openapi|swagger|v1|v2)(\\/|$)/i;
                    if (apiRegex.test(url)) return 'API';
                    
                    const oauthRegex = /^\\/(oauth2|login|logout|saml|oidc|connect|authorize|token)(\\/|$)/i;
                    if (oauthRegex.test(url)) return 'OAUTH';
                    
                    const wsRegex = /^\\/(ws|socket|socket\\.io|sockjs)(\\/|$)/i;
                    if (wsRegex.test(url)) return 'WEBSOCKET';
                    
                    const staticRegex = /\\.(png|jpg|jpeg|gif|webp|avif|svg|ico|css|js|mjs|map|json|txt|xml|webmanifest|woff|woff2|ttf|otf|eot|mp4|webm|mp3|wav|pdf)$/i;
                    if (staticRegex.test(url)) return 'STATIC';
                    
                    return 'SPA';
                  }

                  function rewrite(url) {
                    const type = classify(url);
                    switch(type) {
                      case 'API':
                      case 'OAUTH':
                      case 'WEBSOCKET':
                      case 'SSE':
                        return API_PREFIX + url;
                      case 'STATIC':
                      case 'SPA':
                        return BASE_PATH + url;
                      case 'ALREADY_PREFIXED':
                      case 'EXTERNAL':
                      default:
                        return url;
                    }
                  }

                  // 1. Fetch
                  const originalFetch = window.fetch;
                  window.fetch = function(input, init) {
                    if (typeof input === 'string') {
                      input = rewrite(input);
                    } else if (input instanceof Request) {
                      const type = classify(input.url);
                      if (type !== 'ALREADY_PREFIXED' && type !== 'EXTERNAL') {
                        input = new Request(rewrite(input.url), input);
                      }
                    }
                    return originalFetch.call(this, input, init);
                  };

                  // 2. XHR
                  const originalOpen = XMLHttpRequest.prototype.open;
                  XMLHttpRequest.prototype.open = function(method, url, ...rest) {
                    if (typeof url === 'string') {
                      url = rewrite(url);
                    }
                    return originalOpen.call(this, method, url, ...rest);
                  };

                  // 3. Workers & EventSource & WebSocket
                  const globalsToPatch = ['Worker', 'SharedWorker', 'WebSocket'];
                  globalsToPatch.forEach(name => {
                    if (window[name]) {
                      const Original = window[name];
                      window[name] = function(url, ...rest) {
                        if (typeof url === 'string') {
                          url = rewrite(url);
                        }
                        return new Original(url, ...rest);
                      };
                      window[name].prototype = Original.prototype;
                    }
                  });
                  
                  if (window.EventSource) {
                    const OriginalEventSource = window.EventSource;
                    window.EventSource = function(url, ...rest) {
                      if (typeof url === 'string') {
                        const type = classify(url);
                        if (type !== 'ALREADY_PREFIXED' && type !== 'EXTERNAL') {
                            url = API_PREFIX + url;
                        }
                      }
                      return new OriginalEventSource(url, ...rest);
                    };
                    window.EventSource.prototype = OriginalEventSource.prototype;
                  }
                  
                  // 4. navigator.sendBeacon
                  if (navigator && navigator.sendBeacon) {
                    const originalSendBeacon = navigator.sendBeacon;
                    navigator.sendBeacon = function(url, data) {
                      if (typeof url === 'string') {
                        url = rewrite(url);
                      }
                      return originalSendBeacon.call(this, url, data);
                    };
                  }

                  // 5. createElement and property setters
                  const originalCreateElement = document.createElement;
                  document.createElement = function(tagName, options) {
                    const el = originalCreateElement.call(document, tagName, options);
                    const tag = tagName.toLowerCase();
                    
                    if (tag === 'img' || tag === 'script' || tag === 'iframe' || tag === 'video' || tag === 'audio' || tag === 'source') {
                      let currentSrc = '';
                      Object.defineProperty(el, 'src', {
                        get: function() { return currentSrc; },
                        set: function(val) {
                          currentSrc = val;
                          el.setAttribute('src', rewrite(val));
                        }
                      });
                    }
                    if (tag === 'link' || tag === 'a') {
                      let currentHref = '';
                      Object.defineProperty(el, 'href', {
                        get: function() { return currentHref; },
                        set: function(val) {
                          currentHref = val;
                          el.setAttribute('href', rewrite(val));
                        }
                      });
                    }
                    return el;
                  };

                  // Intercept setAttribute
                  const originalSetAttribute = Element.prototype.setAttribute;
                  Element.prototype.setAttribute = function(name, value) {
                    if ((name === 'src' || name === 'href') && typeof value === 'string') {
                      value = rewrite(value);
                    }
                    return originalSetAttribute.call(this, name, value);
                  };

                  // Patch Image constructor
                  const OriginalImage = window.Image;
                  window.Image = function(width, height) {
                    const img = new OriginalImage(width, height);
                    let currentSrc = '';
                    Object.defineProperty(img, 'src', {
                      get: function() { return currentSrc; },
                      set: function(val) {
                        currentSrc = val;
                        img.setAttribute('src', rewrite(val));
                      }
                    });
                    return img;
                  };

                })();
                </script>
                """.formatted(basePath, basePath, did);

            if (!content.contains("id=\"autopilot-universal-resolver\"")) {
                if (content.contains("<head>")) {
                    content = content.replace("<head>", "<head>\n    " + resolverScript);
                } else if (content.contains("<html>")) {
                    content = content.replace("<html>", "<html>\n    <head>\n    " + resolverScript + "\n    </head>\n");
                } else {
                    content = resolverScript + "\n" + content;
                }
            }

            Files.writeString(htmlFile, content);
            System.out.println("✅ Injected base tag and universal runtime resolver into " + htmlFile.getFileName());
        } catch (Exception e) {
            System.err.println("❌ Failed to inject universal runtime resolver into " + htmlFile + ": " + e.getMessage());
        }
    }

    private void runCmd(String cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("error") || line.toLowerCase().contains("failed")) {
                    System.err.println("[DOCKER] " + line);
                }
            }
        }
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Command failed with exit code " + exit + ": " + cmd);
        }
    }
}
