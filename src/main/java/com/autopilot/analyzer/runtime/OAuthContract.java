package com.autopilot.analyzer.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthContract {
    private boolean supportsOAuth;
    private String callbackUrlPath;
    private List<String> oauthPrefixes;
    private String loginUrl;
    private String provider;
}
