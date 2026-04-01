# Document Domain

## 목적

문서 생성, 조회, 수정, 삭제, 이동, 복구, 휴지통, 버전 정책, workspace 제거까지 문서 도메인 흐름을 다시 찾기 쉽게 묶는다.

## 관련 worklog

- [2026-03-16-document-create.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-document-create.md)
- [2026-03-16-document-read.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-document-read.md)
- [2026-03-16-document-sortkey-not-null.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-document-sortkey-not-null.md)
- [2026-03-17-document-update.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-17-document-update.md)
- [2026-03-18-document-entity-relations.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-18-document-entity-relations.md)
- [2026-03-18-document-delete.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-18-document-delete.md)
- [2026-03-18-version-request-rationale.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-18-version-request-rationale.md)
- [2026-03-20-document-move-api.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-20-document-move-api.md)
- [2026-03-20-document-restore-api.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-20-document-restore-api.md)
- [2026-03-25-document-trash-and-hard-delete.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-25-document-trash-and-hard-delete.md)
- [2026-03-25-document-version-concurrency.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-25-document-version-concurrency.md)
- [2026-03-27-v1-remove-workspace-from-document-flow.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-27-v1-remove-workspace-from-document-flow.md)

## 관련 문서

- [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- [docs/discussions/2026-03-25-document-hard-delete-and-trash-endpoint-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-25-document-hard-delete-and-trash-endpoint-review.md)
- [docs/discussions/2026-03-27-v1-remove-workspace-from-document-flow-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-27-v1-remove-workspace-from-document-flow-review.md)
- [docs/decisions/009-map-document-hierarchy-with-jpa-associations-and-db-cascade.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/009-map-document-hierarchy-with-jpa-associations-and-db-cascade.md)
- [docs/decisions/018-remove-workspace-from-v1-document-flow.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/018-remove-workspace-from-v1-document-flow.md)
- [docs/roadmap/v2/documents/document-trash.md](https://github.com/jho951/Block-server/blob/dev/docs/roadmap/v2/documents/document-trash.md)

## 현재 기준

- 문서 도메인 정책은 `REQUIREMENTS`와 관련 ADR 기준으로 본다.
- 삭제/복구/휴지통 정책과 버전 정책은 이 topic 안의 worklog와 discussion을 함께 봐야 전체 맥락이 잡힌다.

## 열어둘 질문

- workspace 재도입 시 문서 소유 모델을 어떻게 다시 연결할지
- 휴지통 유지 기간과 자동 purge 정책을 운영 기준으로 어떻게 확정할지
