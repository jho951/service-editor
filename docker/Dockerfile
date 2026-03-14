# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

# Gradle Wrapper/빌드파일/소스만 복사 (캐시 최적화)
COPY gradlew /workspace/gradlew
COPY gradle /workspace/gradle
COPY settings.gradle /workspace/settings.gradle
COPY build.gradle /workspace/build.gradle
COPY documents-boot /workspace/documents-boot
COPY documents-api /workspace/documents-api
COPY documents-core /workspace/documents-core
COPY documents-infrastructure /workspace/documents-infrastructure

RUN chmod +x ./gradlew
RUN ./gradlew clean :documents-boot:bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=builder /workspace/documents-boot/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
