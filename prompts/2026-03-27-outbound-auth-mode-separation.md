# 2026-03-27 Outbound Auth Mode Separation

- 목적: 내부 서비스 outbound 호출에서 사용자 위임 토큰과 서비스 전용 토큰 정책을 분리한다.
- Step 1: `OutboundAuthMode`, `ServiceTokenProperties`, `OutboundAuthHeaderFactory`를 추가해 outbound 인증 헤더 생성 정책을 공통 컴포넌트로 구현했다.
- Step 2: `USER_DELEGATION` 모드에서 Bearer 사용자 토큰과 `X-User-Id`, `X-User-Role`, `X-Request-Id` 전파를 지원했다.
- Step 3: `SERVICE_TO_SERVICE` 모드에서 `auth.service-token.bearer-token` 기반 서비스 토큰을 사용하도록 구현했다.
- Step 4: `application-auth.yml`에 `AUTH_SERVICE_BEARER_TOKEN` 설정 키를 추가했다.
- Step 5: unit test(`OutboundAuthHeaderFactoryTest`)로 모드별 성공/실패 시나리오를 검증했다.
- Step 6: `docs/discussions/2026-03-27-outbound-auth-mode-review.md`, `docs/decisions/017-adopt-outbound-auth-mode-separation.md`, `docs/REQUIREMENTS.md`를 갱신했다.
