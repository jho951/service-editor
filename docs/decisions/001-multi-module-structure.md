# ADR 001: Spring Boot 멀티모듈 구조 채택

## 상태

채택됨

## 배경

기존 저장소는 단일 모듈 안에 웹 계층, 도메인 모델, 영속 구현, 실행 설정이 함께 위치해 있었다.
구조가 커질수록 책임 경계가 흐려지고, 빌드 의존성과 변경 영향 범위를 관리하기 어려워진다.

## 결정

다음 4개 모듈로 프로젝트를 분리한다.

- `documents-boot`: 실행 진입점, 환경 설정, 패키징
- `documents-api`: Controller, 요청/응답 DTO, API 전용 예외/응답 코드, OpenAPI 설정
- `documents-core`: 도메인 모델, 공통 엔티티, 서비스 계약
- `documents-infrastructure`: JPA Repository, 영속 구현, 서비스 구현

의존 방향은 `boot -> api`, `boot -> infrastructure`, `api -> core`, `infrastructure -> core`로 제한한다.

## 영향

- 장점:
  - 레이어 경계와 책임이 명확해진다.
  - 기능 확장 시 모듈별 변경 범위를 통제하기 쉬워진다.
  - 실행 모듈과 라이브러리 모듈을 분리해 빌드 구성이 단순해진다.
- 단점:
  - 초기 Gradle 설정과 경로 관리가 단일 모듈보다 복잡하다.
  - 공통 타입 위치와 의존 방향을 지속적으로 관리해야 한다.
