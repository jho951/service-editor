# ADR 004: API 통합 테스트는 boot 모듈에 두고 Controller는 얇게 유지

## 상태

채택됨

## 배경

현재 저장소는 `documents-boot`, `documents-api`, `documents-core`, `documents-infrastructure`로 분리되어 있다.
API 기능을 검증할 때 실제로는 Spring MVC, 공통 예외 처리, Service, Repository, DB 설정이 함께 조립된 실행 컨텍스트가 필요하다.
또한 Controller에 비즈니스 분기와 예외 판단이 쌓이면 이후 API가 늘어날수록 웹 계층이 빠르게 지저분해진다.

## 결정

- Controller 통합 테스트의 기본 위치는 실행 모듈인 `documents-boot`로 고정한다.
- 빠른 확인이 필요한 Controller 계약 검증은 `documents-api`의 slice 테스트로 먼저 수행한다.
- 테스트와 의존성 해석은 저장소 루트 Gradle Wrapper로 수행하고, 실행은 `./gradlew :documents-boot:test`처럼 대상 모듈 task로 제한한다.
- Controller는 요청 매핑, 인증 컨텍스트 전달, Service 호출, 공통 응답 반환만 담당한다.
- 리소스 조회 실패, 정합성 검증, 충돌 판단 등 비즈니스 예외는 Service 계층에서 처리하고 `GlobalExceptionHandler`로 응답을 표준화한다.

## 영향

- 장점:
  - 빠른 slice 테스트와 느린 조립 테스트를 분리해 개발 중 피드백 속도와 운영 안정성을 동시에 확보한다.
  - API 테스트가 실제 애플리케이션 조립 지점에서 실행되어 모듈 연결 문제를 일찍 발견할 수 있다.
  - Controller가 얇게 유지되어 후속 API 구현 시 일관성과 가독성이 높아진다.
  - 에러 처리 정책이 Service 중심으로 정리되어 공통 예외 응답과 결합하기 쉽다.
- 단점:
  - boot 모듈 테스트는 slice 테스트보다 무겁고 실행 시간이 길 수 있다.
  - 테스트 코드가 사용하는 타입에 따라 boot 모듈의 test 전용 의존성을 직접 선언해야 할 수 있다.
