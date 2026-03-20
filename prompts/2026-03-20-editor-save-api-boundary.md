# 2026-03-20 Editor Save API Boundary

- 작업 목적: v1 에디터 저장 기능 기준으로 블록 관련 API의 책임을 재정리하고 `transactions` API 초안을 설계한다.
- 범위: `GET /documents/{documentId}/blocks`, `POST /documents/{documentId}/blocks`, `DELETE /blocks/{blockId}`, `PATCH /blocks/{blockId}`, `POST /documents/{documentId}/transactions`의 경계 검토
- 관련 문서: `docs/discussions/2026-03-20-editor-save-api-boundary-and-transaction-design.md`, `docs/discussions/2026-03-18-block-save-api-strategy.md`, `docs/discussions/2026-03-18-save-api-and-patch-api-coexistence.md`

## Step 1. 기존 API와 저장 경로 점검

- 현재 코드 기준 블록 API는 목록 조회, 생성, 수정, 삭제까지만 구현되어 있음을 확인했다.
- 에디터 저장 경로를 batch transaction으로 가져가려면 `PATCH /v1/blocks/{blockId}`는 책임이 강하게 겹친다고 정리했다.
- 블록 조회는 별도 `GET /content` 없이 `GET /v1/documents/{documentId}/blocks`로도 현재 요구를 충족한다고 정리했다.

## Step 2. 책임 경계 초안

- 에디터 저장의 표준 경로는 `POST /v1/documents/{documentId}/transactions` 하나로 둔다.
- `DELETE /v1/blocks/{blockId}`는 노션식 햄버거 메뉴 subtree 삭제 같은 명시적 단일 액션용 보조 API로 유지 가능하다고 정리했다.
- `POST /v1/documents/{documentId}/blocks`는 명시적 새 블록 생성 경로로 유지하되, 장기적으로 `transactions` 흡수 가능성을 열어두었다.

## Step 3. transaction 초안 정리

- v1 operation은 `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE`까지로 좁혔다.
- structured content 저장은 세밀한 text op가 아니라 block 단위 `replace_content`로 처리하는 쪽이 v1 복잡도에 맞는다고 정리했다.
- debounce 저장에서는 같은 블록의 연속 content 수정은 마지막 상태 하나로 합치고, in-flight 요청은 취소보다 `batchId` 기반 stale ack 무시로 다루는 쪽이 안전하다고 정리했다.

## Step 4. 후속 반영 포인트

- 채택 시 `docs/REQUIREMENTS.md`의 블록 API 목록과 저장 정책을 업데이트해야 한다.
- `PATCH /v1/blocks/{blockId}` 유지/제거와 `transactions`의 partial apply 허용 여부는 후속 결정이 필요하다.

## Step 5. 블록 생성 경계 재정리

- 생성도 에디터 편집 세션 안의 변경이므로 `transactions`에 포함하는 쪽으로 기준을 수정했다.
- 에디터 표준 경로의 v1 operation은 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE` 4개로 정리했다.
- `BLOCK_CREATE`는 위치만 확정하고, 본문 structured content는 같은 batch의 `BLOCK_REPLACE_CONTENT`가 담당하도록 나눴다.
- 이 모델이면 생성 직후 삭제처럼 서버에 보낼 필요 없는 편집을 queue 단계에서 상쇄할 수 있다고 정리했다.

## Step 6. explainer 및 v2 logging roadmap 추가

- 시나리오 중심 설명 문서 `docs/explainers/editor-transaction-save-model.md`를 추가했다.
- 설명 문서에는 에디터 표준 API 경계, 4개 operation 역할, debounce/flush, `Ctrl+A` delete, block version 충돌 시나리오, 실시간 협업과의 차이를 정리했다.
- v1 충돌 정책 추천은 `전체 rollback + conflict block의 최신 version/content 반환`으로 정리했다.
- transaction 로그 테이블은 즉시 도입하지 않고, 운영 실용성이 확인될 때만 검토하도록 `docs/roadmap/v2/blocks/editor-transaction-logging.md`에 후속 후보로 남겼다.

## Step 7. 프론트 구현자용 가이드 추가

- 프론트 구현자가 바로 따라갈 수 있도록 `docs/guides/frontend-editor-transaction-implementation-guide.md`를 추가했다.
- 문서에는 로컬 상태, queue 상태, tempId, coalescing 규칙, flush 상태 머신, 성공/실패/충돌 처리, 구현 순서를 프론트 관점으로 정리했다.
- `AGENTS.md`에 `docs/guides/`, `docs/explainers/` 구조와 guide 정책을 추가했다.

## Step 8. transaction DTO 및 frontend queue 스펙 초안 추가

- `docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md`를 추가했다.
- 문서에는 request/response DTO, conflict 응답, frontend queue 자료구조, coalescing 규칙, flush 상태 모델을 함께 정리했다.
- 구현 전 서버와 프론트가 같은 계약을 보도록 하는 기준선으로 사용한다.

## Step 9. transaction 중심 저장 정책 ADR 채택

- 지금까지 확정한 저장 정책을 `docs/decisions/014-adopt-transaction-centered-editor-save-model.md` ADR로 승격했다.
- ADR에는 에디터 표준 read/write 경로, 4개 operation, 전체 rollback, conflict 최신 content 반환, 단건 block API의 보조 경로화 기준을 정리했다.

## Step 10. max autosave interval 및 프론트/백엔드 처리 단계 보강

- debounce만으로 저장이 무한정 밀리지 않도록 `max autosave interval` 정책을 `docs/REQUIREMENTS.md`와 관련 문서에 반영했다.
- `docs/guides/frontend-editor-transaction-implementation-guide.md`에는 프론트 flush 우선순위와 장시간 연속 입력 보호 규칙을 추가했다.
- `docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md`에는 프론트 전처리 체크리스트와 백엔드 처리 체크리스트를 추가했다.
- 백엔드 구현자용 문서 `docs/guides/backend-editor-transaction-processing-guide.md`를 추가해, 프론트가 정리해 보낸 batch를 서버가 어떻게 검증/반영하는지 단계별로 정리했다.

## Step 11. 기존 content 보관 위치와 pending 생성 시점 설명 보강

- `docs/guides/frontend-editor-transaction-implementation-guide.md`에 로컬 editor state, queue, 서버 snapshot의 역할 구분을 추가했다.
- `BLOCK_REPLACE_CONTENT`는 부분 diff가 아니라 "해당 block의 현재 전체 content"를 보낸다는 점을 더 명시했다.
- 포커스만으로는 pending을 만들지 않고, 실제 편집 시점에 현재 전체 content를 다시 직렬화해 마지막 pending op를 교체하는 기준을 추가했다.
- 새로고침 후 재편집, 다른 작업 후 같은 블록 재편집 시나리오를 추가해, 기존 content를 queue에서 이어 붙이지 않고 로컬 상태 기준으로 다시 pending을 만드는 흐름을 정리했다.

## Step 12. rollback 이후 pending 재조립 규칙 보강

- `docs/guides/frontend-editor-transaction-implementation-guide.md`의 conflict 처리 섹션에, 전체 rollback이 in-flight batch 실패를 의미할 뿐 로컬 draft 폐기를 의미하지 않는다는 점을 더 명시했다.
- 실패한 batch payload를 그대로 복사하는 것이 아니라, 현재 로컬 문서 상태 기준으로 pending queue를 다시 조립한다는 기준을 추가했다.
- 같은 실패 batch 안에 있던 non-conflict move/delete/content 변경도 서버에는 반영되지 않았으므로, 로컬 상태가 유지되고 있다면 다시 pending에 포함될 수 있음을 예시로 정리했다.
- "문서 최신 반영"은 문서 전체 자동 덮어쓰기가 아니라, 충돌 난 block의 최신 서버 snapshot을 로컬 draft와 함께 보관하는 것에 가깝다는 설명을 추가했다.

## Step 13. 채택 직전 문서 세트 최종 정합화

- `docs/REQUIREMENTS.md`에 `tempId`는 클라이언트 로컬 식별자이고, 서버는 실제 `blockId`를 생성해 매핑을 반환한다는 점을 명시했다.
- 같은 문서에 기존 block 수정/이동/삭제는 실제 `blockId`를 사용하고, conflict 후 pending 복구는 현재 로컬 문서 상태 기준 재조립이라는 점을 반영했다.
- `docs/decisions/014-adopt-transaction-centered-editor-save-model.md`에 tempId/real blockId 경계와 rollback 후 재조립 원칙을 추가했다.
- `docs/explainers/editor-transaction-save-model.md`, `docs/guides/backend-editor-transaction-processing-guide.md`, `docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md`에도 같은 기준을 맞춰 넣었다.
