# 2026-03-15 Gradle Version Properties

- 작업 목적: Gradle 버전 상수를 루트 `gradle.properties`로 이동하고 각 모듈이 이를 참조하도록 정리
- 핵심 변경: Java, Spring Boot, dependency-management, springdoc, jakarta persistence, Lombok, JUnit 버전을 프로퍼티화
- 적용 범위: 루트 `build.gradle`, `documents-api/build.gradle`, `documents-core/build.gradle`, `gradle.properties`
- 검증 계획: `./gradlew test`로 멀티모듈 빌드 및 테스트 확인
