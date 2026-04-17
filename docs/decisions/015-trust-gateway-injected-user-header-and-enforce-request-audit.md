# ADR 015: Gateway 주입 `X-User-Id` 신뢰 및 요청 감사 로그 강제

## 상태

채택됨

## 배경

- 이 서비스는 MSA 구조에서 인증의 최종 검증 책임을 Gateway/Auth-service에 둔다.
- 서비스 내부에서는 인증 완료 요청만 처리한다는 전제가 이미 요구사항에 반영되어 있다.
- 기존 구현은 컨트롤러별 `@RequestHeader("X-User-Id")` 의존으로 동작했지만, 누락/빈값 검증과 요청 추적 로그 정책이 공통 계층으로 통합되어 있지 않았다.

## 결정

- `/**` 요청은 공통 인터셉터에서 `X-User-Id`를 검증한다.
- `X-User-Id`가 누락되거나 빈값이면 `401 UNAUTHORIZED`로 처리한다.
- `X-Request-Id`를 공통 인터셉터에서 수신하며, 누락 시 서버가 생성해 응답 헤더에 반영한다.
- 요청 완료 시 `method`, `path`, `status`, `userId`, `requestId`, `durationMs`를 감사 로그로 남긴다.
- 컨트롤러는 헤더를 직접 파싱하지 않고 `@CurrentUserId` 인자 주입으로 인증 컨텍스트를 전달받는다.

## 영향

- 장점:
  - 인증 컨텍스트 검증과 감사 로그 정책의 일관성 확보
  - 컨트롤러 중복 코드 제거 및 계층 책임 원칙 강화
  - 운영 추적성 개선(`userId + requestId`)
- 단점:
  - `/**` 경로가 Gateway 신뢰 경계에 더 강하게 의존
  - 로컬/테스트 환경에서 헤더 누락 시 요청 실패가 더 엄격해짐

## 관련 문서

- [2026-03-27-gateway-trusted-header-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-27-gateway-trusted-header-review.md)
- [REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
