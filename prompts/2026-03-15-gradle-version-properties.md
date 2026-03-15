# 2026-03-15 Gradle Version Properties

- 작업 목적: Gradle 버전 상수를 루트 `gradle.properties`로 이동하고 각 모듈이 이를 참조하도록 정리
- 핵심 변경: Java, Spring Boot, dependency-management, springdoc, jakarta persistence, Lombok, JUnit 버전을 프로퍼티화
- 보정 사항: 플러그인 버전은 `build.gradle`의 `plugins {}`가 아니라 `settings.gradle`의 `pluginManagement`에서 `gradle.properties`를 읽도록 수정
- 안정화 사항: CI에서 `gradle.properties` 일부 값이 없더라도 빌드가 죽지 않도록 `settings.gradle`과 `build.gradle`에 기본값 fallback 추가
- 추가 보정: 모듈 `build.gradle`에서도 `springdocVersion`, `jakartaPersistenceVersion` 직접 참조를 제거하고 루트 fallback 값 사용
- 적용 범위: 루트 `build.gradle`, `documents-api/build.gradle`, `documents-core/build.gradle`, `gradle.properties`
- 검증 계획: `./gradlew test`로 멀티모듈 빌드 및 테스트 확인
