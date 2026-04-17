# Block Domain

## 목적

블록 생성, 조회, 엔티티 관계, 수정, 이동, 삭제, structured content, 복구 정책을 다시 찾기 쉽게 묶는다.

## 관련 worklog

- [2026-03-17-block-create.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-17-block-create.md)
- [2026-03-17-block-list-read.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-17-block-list-read.md)
- [2026-03-18-block-entity-relations.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-18-block-entity-relations.md)
- [2026-03-18-block-update-and-move.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-18-block-update-and-move.md)
- [2026-03-19-block-delete-api-implementation.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-19-block-delete-api-implementation.md)
- [2026-03-19-block-structured-content-migration.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-19-block-structured-content-migration.md)
- [2026-03-20-block-restore-policy.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-20-block-restore-policy.md)

## 관련 문서

- [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- [docs/discussions/2026-03-19-block-structured-content-strategy.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-19-block-structured-content-strategy.md)
- [docs/discussions/2026-03-20-block-restore-policy-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-block-restore-policy-review.md)
- [docs/decisions/010-map-block-hierarchy-with-jpa-associations-and-db-cascade.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/010-map-block-hierarchy-with-jpa-associations-and-db-cascade.md)
- [docs/decisions/011-separate-block-update-from-move-api.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/011-separate-block-update-from-move-api.md)
- [docs/decisions/012-adopt-structured-text-content-and-staged-concurrency-roadmap.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/012-adopt-structured-text-content-and-staged-concurrency-roadmap.md)
- [docs/decisions/013-adopt-session-scoped-browser-undo-and-drop-block-restore-api.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/013-adopt-session-scoped-browser-undo-and-drop-block-restore-api.md)

## 현재 기준

- 블록 수정과 이동은 분리된 API와 정책으로 유지한다.
- structured content와 복구 정책은 discussion, decision, worklog를 함께 봐야 의도가 드러난다.

## 열어둘 질문

- v2 이후 block restore 정책을 다시 열지 여부
- structured content 확장 범위를 어디까지 허용할지
