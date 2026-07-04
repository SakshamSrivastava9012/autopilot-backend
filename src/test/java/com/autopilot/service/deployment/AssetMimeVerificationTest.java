package com.autopilot.service.deployment;

import com.autopilot.service.log.DeploymentLogService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AssetMimeVerificationTest {

    private HttpServer server;
    private int port;
    private HealthCheckService healthCheckService;

    // Custom MIME overrides for testing failures
    private String jsMime = "application/javascript";
    private String jsBody = "console.log('hello');";
    private String cssMime = "text/css";
    private String cssBody = ".body { color: red; }";
    private String svgMime = "image/svg+xml";
    private String svgBody = "<svg></svg>";
    private String icoMime = "image/x-icon";
    private String icoBody = "binarydata";

    private static class DummyLogService extends DeploymentLogService {
        public DummyLogService() {
            super(null, null, null);
        }
        @Override
        public void info(String deploymentId, String stage, String message) {
            // No-op for tests
        }
        @Override
        public void warn(String deploymentId, String stage, String message) {
            // No-op for tests
        }
        @Override
        public void error(String deploymentId, String stage, String message) {
            // No-op for tests
        }
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        // Main HTML handler
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                if (!"/".equals(path) && !"/app-123/".equals(path)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                String html = "<!doctype html>\n" +
                        "<html>\n" +
                        "  <head>\n" +
                        "    <link rel=\"stylesheet\" href=\"/assets/main-123.css\">\n" +
                        "    <link rel=\"icon\" href=\"/favicon.ico\">\n" +
                        "  </head>\n" +
                        "  <body>\n" +
                        "    <script src=\"/assets/index-123.js\"></script>\n" +
                        "    <img src=\"/assets/logo.svg\">\n" +
                        "  </body>\n" +
                        "</html>";
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        // JS Handler
        server.createContext("/assets/index-123.js", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] bytes = jsBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", jsMime);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        // CSS Handler
        server.createContext("/assets/main-123.css", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] bytes = cssBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", cssMime);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        // SVG Handler
        server.createContext("/assets/logo.svg", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] bytes = svgBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", svgMime);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        // Favicon Handler
        server.createContext("/favicon.ico", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] bytes = icoBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", icoMime);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        server.start();

        healthCheckService = new HealthCheckService(null, new DummyLogService(), new ArrayList<>(), new com.autopilot.analyzer.runtime.FrontendRuntimeStrategyRegistry());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testSuccessfulAssetMimeVerification() {
        // Assert no exception is thrown when MIME types and bodies are correct
        assertDoesNotThrow(() -> {
            healthCheckService.verifyAssetsAndSpaRouting("127.0.0.1:" + port, "/app-123", "dep-123");
        });
    }

    @Test
    void testJavascriptAssetMimeMismatch() {
        // Force Javascript response to be text/html
        jsMime = "text/html";
        jsBody = "<!doctype html><html></html>";

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            healthCheckService.verifyAssetsAndSpaRouting("127.0.0.1:" + port, "/app-123", "dep-123");
        });
        assertTrue(ex.getMessage().contains("returned HTML instead of the correct asset MIME type")
                || ex.getMessage().contains("body content starts with HTML markup"));
    }

    @Test
    void testCssAssetMimeMismatch() {
        // Force CSS response to be text/html
        cssMime = "text/html";
        cssBody = "<html><body>Error</body></html>";

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            healthCheckService.verifyAssetsAndSpaRouting("127.0.0.1:" + port, "/app-123", "dep-123");
        });
        assertTrue(ex.getMessage().contains("returned HTML")
                || ex.getMessage().contains("body content starts with HTML markup"));
    }

    @Test
    void testSvgAssetMimeMismatch() {
        // Force SVG response to be text/html
        svgMime = "text/html";
        svgBody = "<html><body>Error</body></html>";

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            healthCheckService.verifyAssetsAndSpaRouting("127.0.0.1:" + port, "/app-123", "dep-123");
        });
        assertTrue(ex.getMessage().contains("returned HTML")
                || ex.getMessage().contains("body content starts with HTML markup"));
    }

    @Test
    void testFaviconMimeMismatch() {
        // Force Favicon response to be text/html
        icoMime = "text/html";
        icoBody = "<!doctype html><html></html>";

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            healthCheckService.verifyAssetsAndSpaRouting("127.0.0.1:" + port, "/app-123", "dep-123");
        });
        assertTrue(ex.getMessage().contains("returned HTML")
                || ex.getMessage().contains("body content starts with HTML markup"));
    }
}
