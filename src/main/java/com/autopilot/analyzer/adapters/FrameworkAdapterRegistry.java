package com.autopilot.analyzer.adapters;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class FrameworkAdapterRegistry {

    private final List<FrameworkAdapter> adapters = new ArrayList<>();

    public FrameworkAdapterRegistry() {
        // Register all core adapters
        adapters.add(new ViteAdapter());
        adapters.add(new ReactCRAAdapter());
        adapters.add(new NextAdapter());
        adapters.add(new AngularAdapter());
        adapters.add(new VueAdapter());
        adapters.add(new NuxtAdapter());
        adapters.add(new SpringBootAdapter());
        adapters.add(new ExpressAdapter());
        adapters.add(new NestJSAdapter());
        adapters.add(new FastAPIAdapter());
        adapters.add(new DjangoAdapter());
        adapters.add(new LaravelAdapter());
        adapters.add(new GoAdapter());
        adapters.add(new RustAdapter());
    }

    public void registerAdapter(FrameworkAdapter adapter) {
        adapters.add(0, adapter); // prioritize custom registered adapters
    }

    public Optional<FrameworkAdapter> findMatchingAdapter(Path workspace, List<String> relativeFiles) {
        return adapters.stream()
                .filter(adapter -> adapter.matches(workspace, relativeFiles))
                .findFirst();
    }
}
