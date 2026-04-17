# 2026-03-27 Gateway Trusted Header Auth Hardening

## Step 1. 작업 요약

- 목적: Gateway 주입 `X-User-Id`를 신뢰 헤더로 통일하고, 누락/빈값 401 및 `userId + requestId` 감사 로그를 공통 계층에서 강제한다.
- 구현: `GatewayAuthInterceptor`를 추가해 `/v1/**` 요청에서 `X-User-Id`를 검증하고, `X-Request-Id` 수신/생성 및 요청 감사 로그를 기록하도록 반영했다.
- 구현: `@CurrentUserId` + `CurrentUserIdArgumentResolver`를 추가하고, 컨트롤러의 직접 `@RequestHeader("X-User-Id")` 파싱을 제거했다.
- 문서: `docs/discussions/2026-03-27-gateway-trusted-header-review.md`, `docs/decisions/015-trust-gateway-injected-user-header-and-enforce-request-audit.md`, `docs/REQUIREMENTS.md`를 갱신했다.
- 검증: `:documents-api:test` 기준으로 컨트롤러/인터셉터 변경 영향 테스트를 수행했다.
