# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

COPY gradlew /workspace/gradlew
COPY gradle /workspace/gradle
COPY settings.gradle /workspace/settings.gradle
COPY build.gradle /workspace/build.gradle
COPY gradle.properties /workspace/gradle.properties
COPY documents-boot /workspace/documents-boot
COPY documents-api /workspace/documents-api
COPY documents-core /workspace/documents-core
COPY documents-infrastructure /workspace/documents-infrastructure

RUN chmod +x ./gradlew
RUN --mount=type=secret,id=gh_token,required=false \
    --mount=type=secret,id=github_actor,required=false \
    if [ -f /run/secrets/gh_token ]; then export GH_TOKEN="$(cat /run/secrets/gh_token)"; fi; \
    if [ -f /run/secrets/github_actor ]; then export GITHUB_ACTOR="$(cat /run/secrets/github_actor)"; fi; \
    ./gradlew clean :documents-boot:bootJar -x test

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=builder /workspace/documents-boot/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","app.jar"]
