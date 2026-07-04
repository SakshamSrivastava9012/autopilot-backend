package com.autopilot.analyzer.detectors;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.FrameworkType;
import com.autopilot.analyzer.model.PackageManager;
import com.autopilot.analyzer.model.RuntimeType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

public class FrontendDetectors {

    @Component
    public static class ReactCraDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "react") &&
                   DetectorUtils.containsDependency(workspace, files, "react-scripts");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "react-cra-app"))
                    .frameworkType(FrameworkType.REACT_CRA)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "react-scripts build")))
                    .startCommand("npx serve -s build -l 3000")
                    .outputDirectory("build")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class ReactViteDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "react") &&
                   DetectorUtils.containsDependency(workspace, files, "vite");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "react-vite-app"))
                    .frameworkType(FrameworkType.REACT_VITE)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "vite build")))
                    .startCommand("npx serve -s dist -l 3000")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class VueCliDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "vue") &&
                   DetectorUtils.containsDependency(workspace, files, "@vue/cli-service");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "vue-cli-app"))
                    .frameworkType(FrameworkType.VUE_CLI)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "vue-cli-service build")))
                    .startCommand("npx serve -s dist -l 3000")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class VueViteDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "vue") &&
                   DetectorUtils.containsDependency(workspace, files, "vite");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "vue-vite-app"))
                    .frameworkType(FrameworkType.VUE_VITE)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "vite build")))
                    .startCommand("npx serve -s dist -l 3000")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class AngularDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "@angular/core");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "angular-app"))
                    .frameworkType(FrameworkType.ANGULAR)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "ng build")))
                    .startCommand("npx serve -s dist -l 3000")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class NextJsDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "next");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "nextjs-app"))
                    .frameworkType(FrameworkType.NEXTJS)
                    .runtimeType(RuntimeType.SSR)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "next build")))
                    .startCommand("npm start -- -p 3000 -H 0.0.0.0")
                    .outputDirectory(".next")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class NuxtDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "nuxt");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "nuxt-app"))
                    .frameworkType(FrameworkType.NUXT)
                    .runtimeType(RuntimeType.SSR)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "nuxt build")))
                    .startCommand("node .output/server/index.mjs")
                    .outputDirectory(".output")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class AstroDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "astro");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "astro-app"))
                    .frameworkType(FrameworkType.ASTRO)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "astro build")))
                    .startCommand("npx serve -s dist -l 3000")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class RemixDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "@remix-run/react") ||
                   DetectorUtils.containsDependency(workspace, files, "@remix-run/node");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "remix-app"))
                    .frameworkType(FrameworkType.REMIX)
                    .runtimeType(RuntimeType.SSR)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "remix build")))
                    .startCommand("npm run start")
                    .outputDirectory("build")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class SvelteDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "svelte") &&
                   !DetectorUtils.containsDependency(workspace, files, "@sveltejs/kit");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "svelte-app"))
                    .frameworkType(FrameworkType.SVELTE)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "rollup -c or vite build")))
                    .startCommand("npx serve -s dist -l 3000")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class SvelteKitDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "@sveltejs/kit");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "sveltekit-app"))
                    .frameworkType(FrameworkType.SVELTEKIT)
                    .runtimeType(RuntimeType.SSR)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "vite build")))
                    .startCommand("node build/index.js")
                    .outputDirectory("build")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class SolidJsDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "solid-js") &&
                   !DetectorUtils.containsDependency(workspace, files, "solid-start") &&
                   !DetectorUtils.containsDependency(workspace, files, "@solidjs/start");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "solidjs-app"))
                    .frameworkType(FrameworkType.SOLIDJS)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "vite build")))
                    .startCommand("npx serve -s dist -l 3000")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class SolidStartDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "solid-start") ||
                   DetectorUtils.containsDependency(workspace, files, "@solidjs/start");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "solidstart-app"))
                    .frameworkType(FrameworkType.SOLIDSTART)
                    .runtimeType(RuntimeType.SSR)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "solid-start build")))
                    .startCommand("node .output/server/index.mjs")
                    .outputDirectory(".output")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class QwikDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "@builder.io/qwik");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "qwik-app"))
                    .frameworkType(FrameworkType.QWIK)
                    .runtimeType(RuntimeType.SSR)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "qwik build")))
                    .startCommand("node server/entry.express.js")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class PreactDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "preact");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "preact-app"))
                    .frameworkType(FrameworkType.PREACT)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "preact build")))
                    .startCommand("npx serve -s build -l 3000")
                    .outputDirectory("build")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class LitDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "lit") ||
                   DetectorUtils.containsDependency(workspace, files, "lit-element") ||
                   DetectorUtils.containsDependency(workspace, files, "lit-html");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "lit-app"))
                    .frameworkType(FrameworkType.LIT)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "tsc")))
                    .startCommand("npx serve -s dist -l 3000")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class ParcelDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "parcel") ||
                   DetectorUtils.containsDependency(workspace, files, "parcel-bundler");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "parcel-app"))
                    .frameworkType(FrameworkType.PARCEL)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "parcel build")))
                    .startCommand("npx serve -s dist -l 3000")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class GatsbyDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "gatsby");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "gatsby-app"))
                    .frameworkType(FrameworkType.GATSBY)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "gatsby build")))
                    .startCommand("npx serve -s public -l 3000")
                    .outputDirectory("public")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class DocusaurusDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "@docusaurus/core");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "docusaurus-app"))
                    .frameworkType(FrameworkType.DOCUSAURUS)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "docusaurus build")))
                    .startCommand("npx serve -s build -l 3000")
                    .outputDirectory("build")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class EleventyDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "@11ty/eleventy");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "eleventy-app"))
                    .frameworkType(FrameworkType.ELEVENTY)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "eleventy")))
                    .startCommand("npx serve -s _site -l 3000")
                    .outputDirectory("_site")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class ViteVanillaDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "vite") &&
                   !DetectorUtils.containsDependency(workspace, files, "react") &&
                   !DetectorUtils.containsDependency(workspace, files, "vue") &&
                   !DetectorUtils.containsDependency(workspace, files, "svelte") &&
                   !DetectorUtils.containsDependency(workspace, files, "solid-js");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "vite-vanilla-app"))
                    .frameworkType(FrameworkType.VITE_VANILLA)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "vite build")))
                    .startCommand("npx serve -s dist -l 3000")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class VanillaHtmlCssJsDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.hasFile(files, "index.html") &&
                   !DetectorUtils.hasFile(files, "package.json") &&
                   !DetectorUtils.hasFile(files, "pom.xml") &&
                   !DetectorUtils.hasFile(files, "build.gradle") &&
                   !DetectorUtils.hasFile(files, "requirements.txt") &&
                   !DetectorUtils.hasFile(files, "go.mod");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "vanilla-app"))
                    .frameworkType(FrameworkType.VANILLA_HTML_CSS_JS)
                    .runtimeType(RuntimeType.STATIC)
                    .packageManager(PackageManager.NONE)
                    .buildCommand("echo 'Vanilla app: no build needed'")
                    .startCommand("npx serve -s . -l 3000")
                    .outputDirectory(".")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("html")
                    .defaultRuntimeVersion("latest")
                    .build();
        }
    }
}
