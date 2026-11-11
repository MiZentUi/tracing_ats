FROM gradle:9.8.2-jdk21 AS build
WORKDIR /app/vacancy-service

# Контекст сборки — корень репозитория: нужен composite build ../text-extraction.
COPY vacancy-service/build.gradle vacancy-service/settings.gradle ./
COPY vacancy-service/lombok.config ./
COPY text-extraction /app/text-extraction
RUN --mount=type=cache,id=vacancy-service-gradle,target=/home/gradle/.gradle \
    gradle dependencies --no-daemon

COPY contracts /app/contracts
COPY vacancy-service/src ./src
RUN --mount=type=cache,id=vacancy-service-gradle,target=/home/gradle/.gradle \
    gradle clean build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN apk --no-cache add curl
EXPOSE 8080
COPY --from=build /app/vacancy-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
