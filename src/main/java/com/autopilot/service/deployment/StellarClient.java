package com.autopilot.service.deployment;

import com.autopilot.config.StellarConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StellarClient {

    private final StellarConfig config;

    public String generate(String prompt) {

        WebClient client = WebClient.builder()
                .baseUrl(config.getUrl()) // e.g. http://localhost:11434/api/generate
                .build();

        Map<String, Object> request = Map.of(
                "model", config.getModel(),
                "prompt", prompt,
                "stream", false
        );

        try {
            Map response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || response.get("response") == null) {
                return null;
            }

            return clean(response.get("response").toString());

        } catch (Exception e) {
            System.err.println("🚨 LLM generate failed");
            e.printStackTrace();
            return null;
        }
    }

    public String generateJson(String prompt) {

        WebClient client = WebClient.builder()
                .baseUrl(config.getUrl())
                .build();

        Map<String, Object> request = Map.of(
                "model", config.getModel(),
                "prompt", prompt,
                "stream", false,
                "format", "json"
        );

        try {
            Map response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || response.get("response") == null) {
                return null;
            }

            return response.get("response").toString()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

        } catch (Exception e) {
            System.err.println("🚨 LLM JSON failed");
            e.printStackTrace();
            return null;
        }
    }

    private String clean(String output) {
        if (output == null) return "";

        output = output.replace("```dockerfile", "")
                .replace("```", "")
                .trim();

        int index = output.indexOf("FROM");
        if (index != -1) {
            output = output.substring(index);
        }

        return output;
    }
}