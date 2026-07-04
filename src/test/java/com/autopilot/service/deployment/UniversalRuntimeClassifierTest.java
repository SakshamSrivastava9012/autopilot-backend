package com.autopilot.service.deployment;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UniversalRuntimeClassifierTest {

    @Test
    public void testUniversalRuntimeClassifierLogic() throws Exception {
        // This is the core logic from AssetPatcherService extracted for testing via Node.js
        String jsScript = """
            const BASE_PATH = "/app-123";
            const API_PREFIX = "/app-123-api";
            
            function classify(url) {
                if (!url || typeof url !== 'string') return 'EXTERNAL';
                if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('//') || url.startsWith('blob:') || url.startsWith('data:') || url.startsWith('mailto:') || url.startsWith('tel:')) return 'EXTERNAL';
                if (!url.startsWith('/')) return 'EXTERNAL';
                
                if (url.startsWith(BASE_PATH + '/') || url === BASE_PATH) return 'ALREADY_PREFIXED';
                if (url.startsWith(API_PREFIX + '/') || url === API_PREFIX) return 'ALREADY_PREFIXED';
                
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
            
            const urls = [
                // SPA + Spring Boot / React + Express / Next.js + API
                "/api/users",
                "/api/auth/register",
                "/rest/v1/products",
                "/graphql",
                
                // OAuth2 login
                "/oauth2/authorization/google",
                "/login/oauth2",
                "/logout",
                "/saml/sso",
                
                // WebSocket applications
                "/ws/chat",
                "/socket.io/?EIO=4",
                
                // Static assets in nested folders
                "/public/sequence/frame.jpg",
                "/images/logo.png",
                "/assets/main.css",
                "/favicon.ico",
                
                // Requests already containing BASE_PATH
                "/app-123/api/auth/register", 
                "/app-123/sequence/frame.jpg",
                "/app-123",
                
                // Requests already containing API_BASE_PATH
                "/app-123-api/api/users",
                "/app-123-api/oauth2/google",
                "/app-123-api",
                
                // External URLs / CDN assets
                "https://google.com/api",
                "http://abc.com/logo.png",
                "//cdn.jsdelivr.net/npm/vue",
                "data:image/png;base64,abc",
                "mailto:test@test.com",
                
                // Relative URLs
                "logo.png",
                "./images/logo.png",
                
                // SPA Navigation
                "/dashboard",
                "/users/profile"
            ];
            
            const results = {};
            urls.forEach(url => {
                results[url] = rewrite(url);
            });
            
            console.log(JSON.stringify(results));
        """;
        
        ProcessBuilder pb = new ProcessBuilder("node", "-e", jsScript);
        Process p = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }
        
        int exitCode = p.waitFor();
        assertEquals(0, exitCode, "Node script execution failed");
        
        String jsonResult = output.toString();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, String> resultMap = mapper.readValue(jsonResult, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
        
        // Assert API Requests
        assertEquals("/app-123-api/api/users", resultMap.get("/api/users"));
        assertEquals("/app-123-api/api/auth/register", resultMap.get("/api/auth/register"));
        assertEquals("/app-123-api/rest/v1/products", resultMap.get("/rest/v1/products"));
        assertEquals("/app-123-api/graphql", resultMap.get("/graphql"));
        
        // Assert OAuth2
        assertEquals("/app-123-api/oauth2/authorization/google", resultMap.get("/oauth2/authorization/google"));
        assertEquals("/app-123-api/login/oauth2", resultMap.get("/login/oauth2"));
        assertEquals("/app-123-api/logout", resultMap.get("/logout"));
        
        // Assert WebSocket
        assertEquals("/app-123-api/ws/chat", resultMap.get("/ws/chat"));
        assertEquals("/app-123-api/socket.io/?EIO=4", resultMap.get("/socket.io/?EIO=4"));
        
        // Assert Static Assets
        assertEquals("/app-123/public/sequence/frame.jpg", resultMap.get("/public/sequence/frame.jpg"));
        assertEquals("/app-123/images/logo.png", resultMap.get("/images/logo.png"));
        assertEquals("/app-123/assets/main.css", resultMap.get("/assets/main.css"));
        assertEquals("/app-123/favicon.ico", resultMap.get("/favicon.ico"));
        
        // Assert Already Prefixed
        assertEquals("/app-123/api/auth/register", resultMap.get("/app-123/api/auth/register"));
        assertEquals("/app-123/sequence/frame.jpg", resultMap.get("/app-123/sequence/frame.jpg"));
        assertEquals("/app-123", resultMap.get("/app-123"));
        assertEquals("/app-123-api/api/users", resultMap.get("/app-123-api/api/users"));
        assertEquals("/app-123-api/oauth2/google", resultMap.get("/app-123-api/oauth2/google"));
        assertEquals("/app-123-api", resultMap.get("/app-123-api"));
        
        // Assert External URLs
        assertEquals("https://google.com/api", resultMap.get("https://google.com/api"));
        assertEquals("http://abc.com/logo.png", resultMap.get("http://abc.com/logo.png"));
        assertEquals("//cdn.jsdelivr.net/npm/vue", resultMap.get("//cdn.jsdelivr.net/npm/vue"));
        assertEquals("data:image/png;base64,abc", resultMap.get("data:image/png;base64,abc"));
        assertEquals("mailto:test@test.com", resultMap.get("mailto:test@test.com"));
        
        // Assert Relative URLs
        assertEquals("logo.png", resultMap.get("logo.png"));
        assertEquals("./images/logo.png", resultMap.get("./images/logo.png"));
        
        // Assert SPA Navigation
        assertEquals("/app-123/dashboard", resultMap.get("/dashboard"));
        assertEquals("/app-123/users/profile", resultMap.get("/users/profile"));
    }
}
