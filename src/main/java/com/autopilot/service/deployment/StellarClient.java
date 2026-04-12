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

        System.out.println("✨ Stellar LLM generating Dockerfile...");

        WebClient client = WebClient.builder()
                .baseUrl(config.getUrl())
                .build();

        Map<String, Object> request = Map.of(
                "model", config.getModel(),
                "prompt", prompt,
                "stream", false
        );

        Map response = client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String result = response.get("response").toString();

        return clean(result);
    }

    private String clean(String output) {

        // remove markdown
        output = output.replace("```dockerfile", "")
                .replace("```", "")
                .trim();

        // 🔥 extract only Dockerfile starting from FROM
        int index = output.indexOf("FROM");
        if (index != -1) {
            output = output.substring(index);
        }

        return output;
    }
}