# 2026-03-27 Internal API Remove V1 Prefix

## Step 1. 내부 API 경로 정리

- 목적: 내부 서비스(auth, editor/block 포함)는 `/v1` 없이 운영하고, 외부 `/v1`는 gateway에서만 제공하도록 정리한다.
- 변경 내용: `documents-api` 컨트롤러 경로에서 `/v1` 프리픽스를 제거했다. (`/workspaces`, `/documents`, `/admin` 기준)
- 변경 내용: `GatewayAuthInterceptor` 적용 경로를 내부 기준으로 재정렬하고, 관련 WebMvc 테스트와 통합 테스트 경로를 모두 `/v1` 없이 갱신했다.

## Step 2. 문서와 채택 기록 정리

- 변경 내용: `docs/REQUIREMENTS.md`, `docs/runbook/DEBUG.md`, 관련 guide/explainer/ADR 문서의 내부 API 경로 표기를 `/v1` 없이 갱신했다.
- 변경 내용: 전략 검토 문서 `docs/discussions/2026-03-27-internal-api-path-without-v1-review.md`와 채택 ADR `docs/decisions/016-remove-v1-prefix-from-internal-apis.md`를 추가했다.
- 검증: 테스트 실행으로 `/v1` 제거 후 API 매핑과 인증 인터셉터 동작을 확인했다.
