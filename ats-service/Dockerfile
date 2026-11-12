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

FROM eclipse-temurin:21-jre-alpine
RUN apk --no-cache add curl
WORKDIR /app
EXPOSE 8080 9090
COPY --from=build /workspace/ats-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
