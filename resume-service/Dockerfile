FROM gradle:9.8.2-jdk21 AS build
# Образ Gradle по умолчанию работает не от root; для записи в /workspace нужен root.
USER root
WORKDIR /workspace

COPY text-extraction ./text-extraction
COPY contracts ./contracts
COPY resume-service ./resume-service

WORKDIR /workspace/resume-service
RUN --mount=type=cache,id=resume-service-gradle,target=/home/gradle/.gradle \
    gradle clean build -x test --no-daemon

# ONNX Runtime для локальной NER требует glibc; alpine (musl) не подходит.
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
EXPOSE 8081
COPY --from=build /workspace/resume-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
