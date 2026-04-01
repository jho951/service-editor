# Editor Transaction

## 목적

에디터 저장 transaction 모델, 배치 처리, temp ref 해석, 동시성, admin 단건 정렬 작업을 다시 찾기 쉽게 묶는다.

## 관련 worklog

- [2026-03-18-version-request-rationale.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-18-version-request-rationale.md)
- [2026-03-20-editor-transaction-save-model.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-20-editor-transaction-save-model.md)
- [2026-03-25-document-version-concurrency.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-25-document-version-concurrency.md)

## 관련 문서

- [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- [docs/discussions/2026-03-20-editor-save-api-boundary-and-transaction-design.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-save-api-boundary-and-transaction-design.md)
- [docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md)
- [docs/decisions/014-adopt-transaction-centered-editor-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/014-adopt-transaction-centered-editor-save-model.md)
- [docs/explainers/editor-transaction-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/explainers/editor-transaction-save-model.md)
- [docs/guides/frontend-editor-transaction-implementation-guide.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/frontend-editor-transaction-implementation-guide.md)
- [docs/guides/backend-editor-transaction-processing-guide.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/backend-editor-transaction-processing-guide.md)

## 현재 기준

- 표준 에디터 저장 경로는 document transaction API 기준으로 본다.
- 단건 block API와 admin API는 transaction 모델과 정합되게 유지한다.
- 동시성은 document version과 block version 정책을 함께 봐야 한다.

## 열어둘 질문

- transaction operation 종류를 더 확장할지 여부
- 프론트 queue/rollback 정책을 v2에서 어떻게 보강할지
