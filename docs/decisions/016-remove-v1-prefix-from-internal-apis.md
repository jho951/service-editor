# ADR 016: 내부 API 경로에서 `/v1` 프리픽스를 제거한다

## 상태

채택됨

## 배경

- 외부 공개 API 버전 정책은 gateway가 담당한다.
- block-server는 gateway 뒤의 내부 서비스로 운영된다.
- 기존에는 내부 컨트롤러 경로가 `/v1`를 직접 포함하고 있어 외부 버전 정책과 내부 구현이 결합되어 있었다.

## 결정

- block-server 내부 API 경로에서 `/v1` 프리픽스를 제거한다.
- 내부 표준 경로는 `/workspaces/**`, `/documents/**`, `/admin/**`를 사용한다.
- gateway는 외부 클라이언트에 `/v1/**`를 제공하고, block-server 전달 전에 `/v1` 프리픽스를 제거(rewrite)한다.
- 인증/감사 인터셉터는 내부 경로 기준으로 동작하며, `X-User-Id` 누락/빈값 요청을 `401`로 차단한다.

## 영향

- 장점:
  - 외부 API 버전 정책과 내부 서비스 라우팅 정책 분리
  - 내부 경로 규칙 단순화
  - gateway 경계를 기준으로 책임 명확화
- 단점:
  - 컨트롤러, 테스트, 문서를 함께 수정해야 함
  - gateway rewrite 누락 시 라우팅 실패 가능

## 관련 문서

- [2026-03-27-internal-api-path-without-v1-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-27-internal-api-path-without-v1-review.md)
- [REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
