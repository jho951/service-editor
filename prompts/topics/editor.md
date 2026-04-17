# Editor

## 목적

editor 기능군의 save, move, editor save 기반 저장 모델, temp ref 해석, 동시성, 관련 가이드를 다시 찾기 쉽게 묶는다.

## 관련 worklog

- [2026-03-18-version-request-rationale.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-18-version-request-rationale.md)
- [2026-03-20-editor-transaction-save-model.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-20-editor-transaction-save-model.md)
- [2026-03-25-document-version-concurrency.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-25-document-version-concurrency.md)
- [2026-04-17-docs-implementation-sync.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-04/2026-04-17-docs-implementation-sync.md)

## 관련 문서

- [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- [docs/discussions/2026-03-20-editor-save-api-boundary-and-transaction-design.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-save-api-boundary-and-transaction-design.md)
- [docs/discussions/2026-04-05-node-domain-abstraction-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-04-05-node-domain-abstraction-review.md)
- [docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md)
- [docs/decisions/014-adopt-transaction-centered-editor-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/014-adopt-transaction-centered-editor-save-model.md)
- [docs/decisions/021-adopt-editor-operation-controller-boundary.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/021-adopt-editor-operation-controller-boundary.md)
- [docs/explainers/editor-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/explainers/editor-save-model.md)
- [docs/guides/editor/editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/editor-guideline.md)
- [docs/guides/editor/frontend-editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/frontend-editor-guideline.md)
- [docs/guides/editor/backend-editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/backend-editor-guideline.md)
- [docs/roadmap/v2/blocks/editor-conflict-response.md](https://github.com/jho951/Block-server/blob/dev/docs/roadmap/v2/blocks/editor-conflict-response.md)

## 현재 기준

- 표준 에디터 저장 경로는 editor operation family의 document save endpoint 기준으로 본다.
- move는 `POST /editor-operations/move` 단일 endpoint에서 문서 이동과 블록 이동을 함께 처리한다.
- move는 drag 중간 상태를 저장하지 않고, drop 확정 시점의 최종 위치만 반영하는 explicit action으로 본다.
- 단건 block API와 admin API는 editor save 모델과 정합되게 유지한다.
- `documentVersion`은 문서 전체 freshness 기준으로 응답에서 갱신하고, 저장/이동 충돌 검출은 요청의 block `version` 기준으로 본다.
- v1 conflict 응답은 공통 실패 응답만 반환하며, 최신 block payload를 포함하는 상세 conflict 응답은 v2 후보로 둔다.

## 열어둘 질문

- editor operation 종류를 더 확장할지 여부
- 프론트 queue/rollback 정책을 v2에서 어떻게 보강할지
