FROM gradle:9.8.2-jdk21 AS build
# Образ Gradle по умолчанию работает не от root; для записи в /workspace нужен root.
USER root
WORKDIR /workspace

COPY text-extraction ./text-extraction
COPY contracts ./contracts
COPY ats-service ./ats-service

WORKDIR /workspace/ats-service
RUN --mount=type=cache,id=ats-service-gradle,target=/home/gradle/.gradle \
    gradle clean build -x test --no-daemon

# ONNX Runtime для локальной NER-модели требует glibc, поэтому не Alpine.
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl libstdc++6 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
EXPOSE 8080 9090
COPY --from=build /workspace/ats-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
