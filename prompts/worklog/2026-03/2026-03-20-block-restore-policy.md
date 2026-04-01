# 2026-03-20 블록 복구 정책 정리

- 작업 목적: 브라우저 세션 undo/redo 방향을 기준으로 v1 블록 복구 API 필요성을 재검토하고 채택 정책을 문서화한다.
- 변경 범위: discussion 회의록, ADR, REQUIREMENTS, roadmap
- 관련 문서: `docs/discussions/2026-03-20-block-restore-policy-review.md`, `docs/decisions/013-adopt-session-scoped-browser-undo-and-drop-block-restore-api.md`

## Step 1. 외부 사례 및 현재 요구사항 검토

- Notion, Google Docs, Confluence 공식 문서를 기준으로 삭제, 복구, 버전 이력 정책을 확인했다.
- 편집 중 undo/redo와 영속 복구가 외부 서비스에서도 분리되는 경향이 있는지 검토했다.
- 현재 저장소 요구사항에서 `block restore`가 어디에 남아 있는지 확인했다.

## Step 2. 채택 결정 문서화

- 브라우저 세션 범위 undo/redo를 v1 기본 정책으로 두고, 세션 종료 후 block 부분 복구는 지원하지 않는 방향을 채택했다.
- `block restore` server API는 v1 범위에서 제외하고, 문서 restore API만 유지하는 ADR을 작성했다.

## Step 3. 요구사항 및 후속 검토 정리

- `docs/REQUIREMENTS.md`에서 `POST /v1/blocks/{blockId}/restore`와 관련 복구 정책을 제거했다.
- block restore는 v2 이후 재검토 항목으로 별도 roadmap에 정리했다.
