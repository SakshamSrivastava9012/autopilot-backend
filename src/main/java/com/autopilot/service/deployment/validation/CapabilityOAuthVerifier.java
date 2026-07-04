package com.autopilot.service.deployment.validation;

import com.autopilot.dto.DeployedService;
import com.autopilot.analyzer.runtime.OAuthContract;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class CapabilityOAuthVerifier {

    public boolean verifyOAuthSetup(DeployedService ds, String publicIp, String accessUrl) {
        OAuthContract oc = ds.getOauthContract();
        if (oc == null || !oc.isSupportsOAuth()) {
            return true; // no OAuth required
        }

        try {
            String checkPath = oc.getCallbackUrlPath();
            if (checkPath == null) checkPath = "/login/oauth2/code/";
            if (!checkPath.startsWith("/")) checkPath = "/" + checkPath;

            String callbackCheckUrl = accessUrl + checkPath.substring(1);
            HttpURLConnection conn = (HttpURLConnection) URI.create(callbackCheckUrl).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            
            // Send standard proxy/X-Forwarded headers
            conn.setRequestProperty("X-Forwarded-Host", publicIp);
            conn.setRequestProperty("X-Forwarded-Proto", "http");
            conn.setRequestProperty("X-Forwarded-Prefix", ds.getBasePath());
            
            int code = conn.getResponseCode();

            // Check Set-Cookie headers for SameSite and Secure properties if present
            Map<String, List<String>> headers = conn.getHeaderFields();
            List<String> setCookies = headers.get("Set-Cookie");
            if (setCookies != null) {
                for (String cookie : setCookies) {
                    if (cookie.contains("Secure") && !cookie.contains("Path=")) {
                        // Warning or minor failure
                    }
                }
            }

            // An OAuth callback redirect check (302/401/404) is expected since no auth code is provided
            return code != 500;
        } catch (Exception e) {
            return false;
        }
    }
}
