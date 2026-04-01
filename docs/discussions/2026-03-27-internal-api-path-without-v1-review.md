# 2026-03-27 내부 API `/v1` 프리픽스 제거 검토 메모

## 문서 목적

- 내부 서비스 API 경로에서 `/v1` 프리픽스를 제거하는 방안을 검토한다.
- gateway가 외부 `/v1`를 유지하면서 내부 경로를 rewrite하는 구조가 필요한 이유를 정리한다.
- 이 문서는 채택 전 전략 비교 메모다.

## 배경

- 현재 block-server는 컨트롤러와 테스트에서 `/v1` 프리픽스를 직접 사용하고 있다.
- 운영 구조상 외부 트래픽은 gateway를 통해서만 유입된다.
- 인증 헤더(`X-User-Id`) 신뢰 경계도 gateway에 맞춰져 있다.

## 검토 범위

- 외부 공개 경로와 내부 서비스 경로의 버전 프리픽스 분리 전략
- gateway rewrite 기준과 block-server 수정 범위

## 핵심 질문

1. 내부 서비스 API가 `/v1`를 직접 유지해야 하는가
2. gateway가 `/v1`를 제거해 전달하는 방식이 운영/개발 측면에서 유리한가

## 고려한 자료와 사례

- `docs/REQUIREMENTS.md`
- `docs/decisions/015-trust-gateway-injected-user-header-and-enforce-request-audit.md`
- `documents-api` 컨트롤러/인터셉터 구현

## 선택지

### 선택지 1. 내부 서비스도 `/v1` 유지

#### 개요

- gateway와 block-server 모두 `/v1` 경로를 사용한다.

#### 시나리오

1. 클라이언트가 `POST /v1/documents/{documentId}/transactions`를 gateway로 호출한다.
2. gateway는 인증만 처리하고 경로를 그대로 내부 서비스에 전달한다.
3. 내부 컨트롤러가 `/v1/**` 매핑으로 요청을 처리한다.

#### 장점

- 기존 코드 변경량이 작다.
- 현재 테스트/문서와 즉시 호환된다.

#### 단점

- 내부 서비스가 외부 버전 정책에 결합된다.
- gateway가 경계 역할을 분명히 하기 어렵다.

### 선택지 2. 내부 서비스 `/v1` 제거 + gateway rewrite

#### 개요

- 외부는 gateway에서만 `/v1/**`를 제공하고, 내부 서비스는 `/v1` 없는 경로를 기준으로 한다.

#### 시나리오

1. 클라이언트가 gateway의 `PATCH /v1/documents/{documentId}`를 호출한다.
2. gateway가 `/v1`를 제거해 `PATCH /documents/{documentId}`로 block-server에 전달한다.
3. block-server는 내부 표준 경로(`/documents/**`, `/workspaces/**`, `/admin/**`)로 요청을 처리한다.

#### 장점

- 외부 버전 정책과 내부 라우팅 정책을 분리할 수 있다.
- 내부 서비스 경로가 단순해지고 테스트/운영 경계가 명확해진다.
- 향후 gateway 버전 전략 변경 시 내부 서비스 영향이 줄어든다.

#### 단점

- 컨트롤러/테스트/문서 경로를 함께 수정해야 한다.
- gateway rewrite 설정이 누락되면 라우팅 오류가 발생한다.

## 비교 요약

- 선택지 1은 단기 변경이 적지만 경계 분리가 약하다.
- 선택지 2는 초기 수정이 크지만 외부 정책과 내부 구현 분리가 명확하다.

## 추천 시나리오

- gateway가 외부 버전(`/v1`)을 유지하고 내부 서비스는 버전 없는 경로를 처리한다.
- 대표 흐름은 `POST /v1/documents/{id}/transactions`(외부) -> `POST /documents/{id}/transactions`(내부)다.

## 현재 추천 방향

- 선택지 2 채택
- block-server의 `/v1` 프리픽스를 제거한다.
- gateway rewrite를 요구사항에 명시한다.

## 미해결 쟁점

1. gateway 배포 설정에서 rewrite 누락을 자동 검증하는 방법
2. 외부 API 문서에서 gateway 경로와 내부 경로를 어떻게 분리 표기할지

## 다음 액션

1. 컨트롤러/테스트 `/v1` 제거 반영
2. REQUIREMENTS, runbook 동기화
3. 채택 내용 ADR 승격

## 관련 문서

- `docs/decisions/016-remove-v1-prefix-from-internal-apis.md`
- `docs/REQUIREMENTS.md`
- [prompts/worklog/2026-03/2026-03-27-internal-api-remove-v1-prefix.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-27-internal-api-remove-v1-prefix.md)
