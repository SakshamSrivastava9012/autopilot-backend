FROM maven:3.9.9-eclipse-temurin-{{RUNTIME_VERSION}} AS builder

WORKDIR /build

COPY . .

RUN chmod +x mvnw gradlew 2>/dev/null || true
RUN {{BUILD_COMMAND}}

FROM eclipse-temurin:{{RUNTIME_VERSION}}-jre

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE {{PORT}}

ENTRYPOINT ["java", "-jar", "app.jar"]