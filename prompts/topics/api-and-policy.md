# API And Policy

## 목적

컨트롤러 정책, 테스트 정책, 예외 정책, 네이밍/유틸리티/포맷터 기준, 인증 헤더 정책, 내부 API 경로 정책, outbound auth 정책을 다시 찾기 쉽게 묶는다.

## 관련 worklog

- [2026-03-14-readme-overview.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-14-readme-overview.md)
- [2026-03-15-drawing-name-cleanup.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-15-drawing-name-cleanup.md)
- [2026-03-15-hexagonal-layer-cleanup.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-15-hexagonal-layer-cleanup.md)
- [2026-03-16-api-testing-and-controller-policy.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-api-testing-and-controller-policy.md)
- [2026-03-16-auth-entry-and-validation-minimization.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-auth-entry-and-validation-minimization.md)
- [2026-03-16-global-exception-unification.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-global-exception-unification.md)
- [2026-03-16-naver-formatter-policy.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-naver-formatter-policy.md)
- [2026-03-16-pk-column-naming.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-pk-column-naming.md)
- [2026-03-16-service-utility-separation.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-service-utility-separation.md)
- [2026-03-16-test-pyramid-policy.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-test-pyramid-policy.md)
- [2026-03-24-controller-refactor.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-24-controller-refactor.md)
- [2026-03-27-gateway-trusted-header-auth-hardening.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-27-gateway-trusted-header-auth-hardening.md)
- [2026-03-27-internal-api-remove-v1-prefix.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-27-internal-api-remove-v1-prefix.md)
- [2026-03-27-outbound-auth-mode-separation.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-27-outbound-auth-mode-separation.md)
- [2026-04-05-node-domain-abstraction-review.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-04/2026-04-05-node-domain-abstraction-review.md)
- [2026-04-17-docs-implementation-sync.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-04/2026-04-17-docs-implementation-sync.md)

## 관련 문서

- [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- [docs/discussions/2026-03-27-gateway-trusted-header-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-27-gateway-trusted-header-review.md)
- [docs/discussions/2026-03-27-internal-api-path-without-v1-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-27-internal-api-path-without-v1-review.md)
- [docs/discussions/2026-03-27-outbound-auth-mode-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-27-outbound-auth-mode-review.md)
- [docs/discussions/2026-04-05-node-domain-abstraction-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-04-05-node-domain-abstraction-review.md)
- [docs/decisions/015-trust-gateway-injected-user-header-and-enforce-request-audit.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/015-trust-gateway-injected-user-header-and-enforce-request-audit.md)
- [docs/decisions/016-remove-v1-prefix-from-internal-apis.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/016-remove-v1-prefix-from-internal-apis.md)
- [docs/decisions/017-adopt-outbound-auth-mode-separation.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/017-adopt-outbound-auth-mode-separation.md)
- [docs/decisions/021-adopt-editor-operation-controller-boundary.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/021-adopt-editor-operation-controller-boundary.md)
- [docs/guides/editor/editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/editor-guideline.md)

## 현재 기준

- API 계층 정책과 인증 헤더 정책은 discussion과 decision을 함께 보고 해석한다.
- editor 성격의 write API는 `EditorOperationController` 경계로 모은다.
- 컨트롤러/테스트/예외/유틸리티 기준은 초기 정책 worklog와 이후 하드닝 작업을 함께 봐야 일관성이 드러난다.

## 열어둘 질문

- 내부 API와 외부 gateway 경계 문서를 guide로 더 분리할지 여부
- outbound auth mode를 호출 대상별로 더 세분화할지 여부
