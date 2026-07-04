package com.autopilot.analyzer.detectors;

import com.autopilot.analyzer.model.FrameworkMetadata;
import com.autopilot.analyzer.model.FrameworkType;
import com.autopilot.analyzer.model.PackageManager;
import com.autopilot.analyzer.model.RuntimeType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BackendDetectors {

    // === JAVA ===

    @Component
    public static class SpringBootDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            boolean hasPom = DetectorUtils.hasFile(files, "pom.xml");
            boolean hasGradle = DetectorUtils.hasFile(files, "build.gradle");
            if (hasPom) {
                try {
                    String content = Files.readString(workspace.resolve("pom.xml"));
                    return content.contains("spring-boot");
                } catch (IOException ignored) {}
            }
            if (hasGradle) {
                try {
                    String content = Files.readString(workspace.resolve("build.gradle"));
                    return content.contains("org.springframework.boot");
                } catch (IOException ignored) {}
            }
            return false;
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            boolean isMaven = DetectorUtils.hasFile(files, "pom.xml");
            PackageManager pm = isMaven ? PackageManager.MAVEN : PackageManager.GRADLE;
            String buildCmd = isMaven ? "./mvnw clean package -DskipTests" : "./gradlew build -x test";
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "spring-boot-app"))
                    .frameworkType(FrameworkType.SPRING_BOOT)
                    .runtimeType(RuntimeType.JAVA_JAR)
                    .packageManager(pm)
                    .buildCommand(buildCmd)
                    .startCommand("java -jar target/*.jar")
                    .outputDirectory(isMaven ? "target" : "build/libs")
                    .port(8080)
                    .healthCheckPath("/actuator/health")
                    .language("java")
                    .defaultRuntimeVersion("21")
                    .build();
        }
    }

    @Component
    public static class QuarkusDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            boolean hasPom = DetectorUtils.hasFile(files, "pom.xml");
            boolean hasGradle = DetectorUtils.hasFile(files, "build.gradle");
            if (hasPom) {
                try {
                    String content = Files.readString(workspace.resolve("pom.xml"));
                    return content.contains("quarkus");
                } catch (IOException ignored) {}
            }
            if (hasGradle) {
                try {
                    String content = Files.readString(workspace.resolve("build.gradle"));
                    return content.contains("quarkus");
                } catch (IOException ignored) {}
            }
            return false;
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            boolean isMaven = DetectorUtils.hasFile(files, "pom.xml");
            PackageManager pm = isMaven ? PackageManager.MAVEN : PackageManager.GRADLE;
            String buildCmd = isMaven ? "./mvnw package" : "./gradlew build";
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "quarkus-app"))
                    .frameworkType(FrameworkType.QUARKUS)
                    .runtimeType(RuntimeType.JAVA_JAR)
                    .packageManager(pm)
                    .buildCommand(buildCmd)
                    .startCommand("java -jar target/quarkus-app/quarkus-run.jar")
                    .outputDirectory(isMaven ? "target/quarkus-app" : "build/quarkus-app")
                    .port(8080)
                    .healthCheckPath("/q/health")
                    .language("java")
                    .defaultRuntimeVersion("21")
                    .build();
        }
    }

    @Component
    public static class MicronautDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            boolean hasPom = DetectorUtils.hasFile(files, "pom.xml");
            boolean hasGradle = DetectorUtils.hasFile(files, "build.gradle");
            if (hasPom) {
                try {
                    String content = Files.readString(workspace.resolve("pom.xml"));
                    return content.contains("micronaut");
                } catch (IOException ignored) {}
            }
            if (hasGradle) {
                try {
                    String content = Files.readString(workspace.resolve("build.gradle"));
                    return content.contains("micronaut");
                } catch (IOException ignored) {}
            }
            return false;
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            boolean isMaven = DetectorUtils.hasFile(files, "pom.xml");
            PackageManager pm = isMaven ? PackageManager.MAVEN : PackageManager.GRADLE;
            String buildCmd = isMaven ? "./mvnw package" : "./gradlew build";
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "micronaut-app"))
                    .frameworkType(FrameworkType.MICRONAUT)
                    .runtimeType(RuntimeType.JAVA_JAR)
                    .packageManager(pm)
                    .buildCommand(buildCmd)
                    .startCommand("java -jar target/*.jar")
                    .outputDirectory(isMaven ? "target" : "build/libs")
                    .port(8080)
                    .healthCheckPath("/health")
                    .language("java")
                    .defaultRuntimeVersion("21")
                    .build();
        }
    }

    // === NODE BACKENDS ===

    @Component
    public static class ExpressDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "express");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "express-app"))
                    .frameworkType(FrameworkType.EXPRESS)
                    .runtimeType(RuntimeType.NODE_SERVER)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "")))
                    .startCommand(DetectorUtils.getPackageJsonScript(workspace, files, "start", "node index.js"))
                    .outputDirectory(".")
                    .port(8080)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class NestJsDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "@nestjs/core");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "nestjs-app"))
                    .frameworkType(FrameworkType.NESTJS)
                    .runtimeType(RuntimeType.NODE_SERVER)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "nest build")))
                    .startCommand("node dist/main.js")
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("typescript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class FastifyDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "fastify");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "fastify-app"))
                    .frameworkType(FrameworkType.FASTIFY)
                    .runtimeType(RuntimeType.NODE_SERVER)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "")))
                    .startCommand(DetectorUtils.getPackageJsonScript(workspace, files, "start", "node app.js"))
                    .outputDirectory(".")
                    .port(3000)
                    .healthCheckPath("/health")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class KoaDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "koa");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "koa-app"))
                    .frameworkType(FrameworkType.KOA)
                    .runtimeType(RuntimeType.NODE_SERVER)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "")))
                    .startCommand(DetectorUtils.getPackageJsonScript(workspace, files, "start", "node app.js"))
                    .outputDirectory(".")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("javascript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class HonoDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "hono");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "hono-app"))
                    .frameworkType(FrameworkType.HONO)
                    .runtimeType(RuntimeType.NODE_SERVER)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "vite build or bun build")))
                    .startCommand(DetectorUtils.getPackageJsonScript(workspace, files, "start", "node dist/index.js"))
                    .outputDirectory("dist")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("typescript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    @Component
    public static class AdonisDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.containsDependency(workspace, files, "@adonisjs/core");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            PackageManager pm = DetectorUtils.detectNodePackageManager(workspace, files);
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "adonis-app"))
                    .frameworkType(FrameworkType.ADONIS)
                    .runtimeType(RuntimeType.NODE_SERVER)
                    .packageManager(pm)
                    .buildCommand(DetectorUtils.getBuildCommand(pm, DetectorUtils.getPackageJsonScript(workspace, files, "build", "node ace build")))
                    .startCommand("node build/server.js")
                    .outputDirectory("build")
                    .port(3333)
                    .healthCheckPath("/")
                    .language("typescript")
                    .defaultRuntimeVersion("20")
                    .build();
        }
    }

    // === PYTHON BACKENDS ===

    @Component
    public static class DjangoDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            if (DetectorUtils.hasFile(files, "requirements.txt")) {
                try {
                    String content = Files.readString(workspace.resolve("requirements.txt"));
                    return content.toLowerCase().contains("django");
                } catch (IOException ignored) {}
            }
            return DetectorUtils.hasFile(files, "manage.py");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "django-app"))
                    .frameworkType(FrameworkType.DJANGO)
                    .runtimeType(RuntimeType.PYTHON)
                    .packageManager(PackageManager.PIP)
                    .buildCommand("pip install -r requirements.txt")
                    .startCommand("gunicorn config.wsgi:application --bind 0.0.0.0:8000")
                    .outputDirectory(".")
                    .port(8000)
                    .healthCheckPath("/")
                    .language("python")
                    .defaultRuntimeVersion("3.10")
                    .build();
        }
    }

    @Component
    public static class FlaskDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            if (DetectorUtils.hasFile(files, "requirements.txt")) {
                try {
                    String content = Files.readString(workspace.resolve("requirements.txt"));
                    return content.toLowerCase().contains("flask");
                } catch (IOException ignored) {}
            }
            return false;
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "flask-app"))
                    .frameworkType(FrameworkType.FLASK)
                    .runtimeType(RuntimeType.PYTHON)
                    .packageManager(PackageManager.PIP)
                    .buildCommand("pip install -r requirements.txt")
                    .startCommand("gunicorn app:app --bind 0.0.0.0:5000")
                    .outputDirectory(".")
                    .port(5000)
                    .healthCheckPath("/")
                    .language("python")
                    .defaultRuntimeVersion("3.10")
                    .build();
        }
    }

    @Component
    public static class FastApiDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            if (DetectorUtils.hasFile(files, "requirements.txt")) {
                try {
                    String content = Files.readString(workspace.resolve("requirements.txt"));
                    return content.toLowerCase().contains("fastapi");
                } catch (IOException ignored) {}
            }
            return false;
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "fastapi-app"))
                    .frameworkType(FrameworkType.FASTAPI)
                    .runtimeType(RuntimeType.PYTHON)
                    .packageManager(PackageManager.PIP)
                    .buildCommand("pip install -r requirements.txt")
                    .startCommand("uvicorn main:app --host 0.0.0.0 --port 8000")
                    .outputDirectory(".")
                    .port(8000)
                    .healthCheckPath("/docs")
                    .language("python")
                    .defaultRuntimeVersion("3.10")
                    .build();
        }
    }

    // === PHP ===

    @Component
    public static class LaravelDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            if (DetectorUtils.hasFile(files, "composer.json")) {
                try {
                    String content = Files.readString(workspace.resolve("composer.json"));
                    return content.contains("laravel/framework");
                } catch (IOException ignored) {}
            }
            return false;
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "laravel-app"))
                    .frameworkType(FrameworkType.LARAVEL)
                    .runtimeType(RuntimeType.PHP)
                    .packageManager(PackageManager.COMPOSER)
                    .buildCommand("composer install --no-dev --optimize-autoloader")
                    .startCommand("php artisan serve --host=0.0.0.0 --port=8000")
                    .outputDirectory("public")
                    .port(8000)
                    .healthCheckPath("/")
                    .language("php")
                    .defaultRuntimeVersion("8.2")
                    .build();
        }
    }

    @Component
    public static class SymfonyDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            if (DetectorUtils.hasFile(files, "composer.json")) {
                try {
                    String content = Files.readString(workspace.resolve("composer.json"));
                    return content.contains("symfony/framework-bundle");
                } catch (IOException ignored) {}
            }
            return false;
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "symfony-app"))
                    .frameworkType(FrameworkType.SYMFONY)
                    .runtimeType(RuntimeType.PHP)
                    .packageManager(PackageManager.COMPOSER)
                    .buildCommand("composer install --no-dev --optimize-autoloader")
                    .startCommand("php -S 0.0.0.0:8000 -t public")
                    .outputDirectory("public")
                    .port(8000)
                    .healthCheckPath("/")
                    .language("php")
                    .defaultRuntimeVersion("8.2")
                    .build();
        }
    }

    // === GO ===

    @Component
    public static class GoDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.hasFile(files, "go.mod");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "go-app"))
                    .frameworkType(FrameworkType.GO)
                    .runtimeType(RuntimeType.GO_BINARY)
                    .packageManager(PackageManager.GO)
                    .buildCommand("go build -o server .")
                    .startCommand("./server")
                    .outputDirectory(".")
                    .port(8080)
                    .healthCheckPath("/")
                    .language("go")
                    .defaultRuntimeVersion("1.22")
                    .build();
        }
    }

    // === RUST ===

    @Component
    public static class RustDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return DetectorUtils.hasFile(files, "Cargo.toml");
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "rust-app"))
                    .frameworkType(FrameworkType.RUST)
                    .runtimeType(RuntimeType.RUST_BINARY)
                    .packageManager(PackageManager.CARGO)
                    .buildCommand("cargo build --release")
                    .startCommand("./target/release/app")
                    .outputDirectory("target/release")
                    .port(8080)
                    .healthCheckPath("/")
                    .language("rust")
                    .defaultRuntimeVersion("1.77")
                    .build();
        }
    }

    // === .NET ===

    @Component
    public static class DotNetDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            return files.stream().anyMatch(f -> f.endsWith(".csproj") || f.endsWith(".sln"));
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            String projectName = "dotnet-app";
            for (String file : files) {
                if (file.endsWith(".csproj")) {
                    projectName = Path.of(file).getFileName().toString().replace(".csproj", "");
                    break;
                }
            }
            return FrameworkMetadata.builder()
                    .name(projectName)
                    .frameworkType(FrameworkType.DOTNET)
                    .runtimeType(RuntimeType.DOTNET_BINARY)
                    .packageManager(PackageManager.NUGET)
                    .buildCommand("dotnet publish -c Release -o out")
                    .startCommand("dotnet out/" + projectName + ".dll")
                    .outputDirectory("out")
                    .port(8080)
                    .healthCheckPath("/")
                    .language("csharp")
                    .defaultRuntimeVersion("8.0")
                    .build();
        }
    }

    // === RUBY ON RAILS ===

    @Component
    public static class RubyOnRailsDetector implements FrameworkDetector {
        @Override
        public boolean matches(Path workspace, List<String> files) {
            if (DetectorUtils.hasFile(files, "Gemfile")) {
                try {
                    String content = Files.readString(workspace.resolve("Gemfile"));
                    return content.contains("gem 'rails'") || content.contains("gem \"rails\"");
                } catch (IOException ignored) {}
            }
            return false;
        }

        @Override
        public FrameworkMetadata detect(Path workspace, List<String> files) {
            return FrameworkMetadata.builder()
                    .name(DetectorUtils.deriveServiceName(files, "rails-app"))
                    .frameworkType(FrameworkType.RUBY_ON_RAILS)
                    .runtimeType(RuntimeType.RUBY_ON_RAILS)
                    .packageManager(PackageManager.GEM)
                    .buildCommand("bundle install")
                    .startCommand("bundle exec rails server -b 0.0.0.0 -p 3000")
                    .outputDirectory(".")
                    .port(3000)
                    .healthCheckPath("/")
                    .language("ruby")
                    .defaultRuntimeVersion("3.2")
                    .build();
        }
    }
}
