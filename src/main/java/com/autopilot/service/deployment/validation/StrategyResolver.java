package com.autopilot.service.deployment.validation;

import com.autopilot.analyzer.model.ServiceConfig;
import org.springframework.stereotype.Component;

@Component
public class StrategyResolver {

    public FrameworkStrategy resolve(ServiceConfig service) {
        String framework = service.getFramework() != null ? service.getFramework().toLowerCase() : "";
        String language = service.getLanguage() != null ? service.getLanguage().toLowerCase() : "";

        if (framework.contains("spring") || framework.contains("quarkus") || language.equals("java") || language.equals("kotlin")) {
            return new SpringBootFrameworkStrategy(service);
        } else if (framework.contains("react") || framework.contains("next") || framework.contains("vue") || 
                   framework.contains("node") || framework.contains("express") || framework.contains("nest")) {
            return new ReactViteFrameworkStrategy(service);
        } else if (language.equals("python")) {
            return new PythonFrameworkStrategy(service);
        } else if (language.equals("go")) {
            return new GoFrameworkStrategy(service);
        } else if (language.equals("rust")) {
            return new RustFrameworkStrategy(service);
        } else if (language.equals("dotnet") || framework.contains("dotnet")) {
            return new DotNetFrameworkStrategy(service);
        } else if (framework.contains("laravel") || language.equals("php")) {
            return new LaravelFrameworkStrategy(service);
        } else {
            return new GenericFrameworkStrategy(service);
        }
    }
}
