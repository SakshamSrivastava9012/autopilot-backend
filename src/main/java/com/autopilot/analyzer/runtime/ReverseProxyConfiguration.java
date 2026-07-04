package com.autopilot.analyzer.runtime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReverseProxyConfiguration {
    private String proxyPassUrl;
    private boolean rewritePrefix;
    private String customHeaders;
}
