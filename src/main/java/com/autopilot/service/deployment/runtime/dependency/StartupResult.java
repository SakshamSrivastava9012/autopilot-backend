package com.autopilot.service.deployment.runtime.dependency;

import java.util.List;

public record StartupResult(boolean success, List<String> commands, String error) {}
