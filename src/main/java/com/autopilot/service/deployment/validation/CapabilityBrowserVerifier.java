package com.autopilot.service.deployment.validation;

import com.autopilot.dto.DeployedService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CapabilityBrowserVerifier {

    private static final String SCRIPT_DIR = System.getProperty("user.home") + "/.autopilot-puppeteer";

    public List<String> verifyInBrowser(DeployedService ds, String publicIp, String accessUrl) {
        List<String> failures = new ArrayList<>();
        System.out.println("🌐 Starting Browser-Level Validation for: " + accessUrl);

        try {
            setupPuppeteerEnvironment();

            String scriptPath = SCRIPT_DIR + "/validate.js";
            String resultFile = SCRIPT_DIR + "/result-" + UUID.randomUUID() + ".json";

            ProcessBuilder pb = new ProcessBuilder("node", scriptPath, accessUrl, resultFile, ds.getBasePath());
            pb.directory(new File(SCRIPT_DIR));
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[PUPPETEER] " + line);
                }
            }

            int exitCode = p.waitFor();

            File resFile = new File(resultFile);
            if (resFile.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> result = mapper.readValue(resFile, Map.class);
                
                List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
                if (errors != null && !errors.isEmpty()) {
                    for (Map<String, Object> err : errors) {
                        String type = (String) err.get("type");
                        String url = (String) err.get("url");
                        String msg = (String) err.get("message");
                        
                        String formatted = "FAILED [" + type + "] ";
                        if (url != null) formatted += url + " ";
                        if (msg != null) formatted += "- " + msg;
                        
                        failures.add(formatted);
                    }
                }
                resFile.delete();
            } else {
                failures.add("Browser validation failed: No result file generated. Exit code: " + exitCode);
            }

        } catch (Exception e) {
            failures.add("Failed to execute browser validation: " + e.getMessage());
            e.printStackTrace();
        }

        return failures;
    }

    private void setupPuppeteerEnvironment() throws Exception {
        Path dir = Paths.get(SCRIPT_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        Path packageJson = dir.resolve("package.json");
        if (!Files.exists(packageJson)) {
            Files.writeString(packageJson, "{ \"name\": \"autopilot-validator\", \"dependencies\": { \"puppeteer\": \"^21.0.0\" } }");
            
            System.out.println("📦 Installing Puppeteer in " + SCRIPT_DIR + " (This may take a minute...)");
            ProcessBuilder pb = new ProcessBuilder("npm", "install");
            pb.directory(dir.toFile());
            Process p = pb.start();
            p.waitFor();
        }

        Path scriptPath = dir.resolve("validate.js");
        String script = "const puppeteer = require('puppeteer');\n" +
                "const fs = require('fs');\n" +
                "const args = process.argv.slice(2);\n" +
                "const targetUrl = args[0];\n" +
                "const resultFile = args[1];\n" +
                "const basePath = args[2];\n" +
                "\n" +
                "if (!targetUrl || !resultFile) {\n" +
                "  console.error('Usage: node validate.js <url> <resultFile> <basePath>');\n" +
                "  process.exit(1);\n" +
                "}\n" +
                "\n" +
                "(async () => {\n" +
                "  const browser = await puppeteer.launch({\n" +
                "    headless: 'new',\n" +
                "    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-web-security']\n" +
                "  });\n" +
                "  const page = await browser.newPage();\n" +
                "  const errors = [];\n" +
                "\n" +
                "  page.on('pageerror', err => {\n" +
                "    errors.push({ type: 'JS_EXCEPTION', message: err.toString() });\n" +
                "  });\n" +
                "\n" +
                "  page.on('console', msg => {\n" +
                "    if (msg.type() === 'error') {\n" +
                "      errors.push({ type: 'CONSOLE_ERROR', message: msg.text() });\n" +
                "    }\n" +
                "  });\n" +
                "\n" +
                "  page.on('requestfailed', request => {\n" +
                "    errors.push({ \n" +
                "      type: 'NETWORK_FAILURE', \n" +
                "      url: request.url(), \n" +
                "      message: request.failure().errorText \n" +
                "    });\n" +
                "  });\n" +
                "\n" +
                "  page.on('response', response => {\n" +
                "    const status = response.status();\n" +
                "    if (status >= 400 && status !== 401 && status !== 403) {\n" +
                "      const url = response.url();\n" +
                "      // Ignore 404s for favicon\n" +
                "      if (!url.includes('favicon.ico')) {\n" +
                "        errors.push({ \n" +
                "          type: 'HTTP_ERROR',\n" +
                "          url: url,\n" +
                "          message: 'Status ' + status\n" +
                "        });\n" +
                "      }\n" +
                "    }\n" +
                "  });\n" +
                "\n" +
                "  try {\n" +
                "    await page.goto(targetUrl, { waitUntil: 'networkidle0', timeout: 15000 });\n" +
                "  } catch (e) {\n" +
                "    errors.push({ type: 'NAVIGATION_ERROR', message: e.message });\n" +
                "  }\n" +
                "\n" +
                "  await browser.close();\n" +
                "\n" +
                "  fs.writeFileSync(resultFile, JSON.stringify({ errors: errors }, null, 2));\n" +
                "  process.exit(0);\n" +
                "})();";
        
        // Always overwrite to ensure latest version
        Files.writeString(scriptPath, script);
    }
}
