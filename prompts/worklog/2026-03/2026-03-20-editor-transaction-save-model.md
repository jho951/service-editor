# 2026-03-20 Editor Transaction Save Model

## Step 1. 저장 API 경계와 transaction 초안 정리

- 목적: v1 에디터 저장 기능 기준으로 블록 관련 API의 책임을 재정리하고 `transactions` API 초안을 설계한다.
- 변경 내용: 에디터 표준 저장 경로를 `POST /v1/documents/{documentId}/transactions`로 두고, v1 operation을 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE`로 정리했다.
- 판단: `PATCH /v1/blocks/{blockId}`는 책임이 강하게 겹치므로 에디터 표준 경로와 분리하고, `DELETE /v1/blocks/{blockId}`와 `POST /v1/documents/{documentId}/blocks`는 명시적 단일 액션용 보조 API로 유지 가능하다고 정리했다.

## Step 2. 설명 문서와 구현 가이드 정리

- 변경 내용: `docs/explainers/editor-transaction-save-model.md`, `docs/guides/frontend-editor-transaction-implementation-guide.md`, `docs/guides/backend-editor-transaction-processing-guide.md`, `docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md`를 추가하거나 갱신했다.
- 변경 내용: debounce, max autosave interval, queue 상태, tempId, rollback 이후 pending 재조립, conflict 처리, 프론트/백엔드 처리 체크리스트를 문서화했다.
- 결정 기록: `docs/decisions/014-adopt-transaction-centered-editor-save-model.md`로 채택 내용을 승격했다.
- 설명 보강: block update의 version 충돌 시나리오를 실제 텍스트 수정 흐름 기준으로 설명하도록 `docs/REQUIREMENTS.md`와 관련 discussion 문서를 보강했다.

## Step 3. create + replace_content 최소 경로 구현

- 변경 내용: `POST /v1/documents/{documentId}/transactions` 엔드포인트와 request/response DTO를 추가했다.
- 구현 범위: 첫 구현은 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT` 두 operation만 처리하고, `tempId -> real blockId` 매핑과 applied operation 응답을 반환하도록 연결했다.
- 구현 내용: `documents-core`에 transaction command/result 모델과 서비스 인터페이스를 추가하고, `documents-infrastructure`에 transaction orchestration 구현을 추가했다.
- 검증: WebMvc, 서비스, Boot 통합 테스트로 최소 경로를 확인했다.

## Step 4. request/ref 모델 통일과 temp 위치 해석 확장

- 변경 내용: transaction request의 블록 참조 필드를 `blockRef` 하나로 통일하고, 위치 참조도 `parentRef`, `afterRef`, `beforeRef`로 재정의했다.
- 구현 내용: create 시 `parentRef`, `afterRef`, `beforeRef`를 `tempId -> real blockId` 컨텍스트로 해석하도록 확장했다.
- 테스트 보강: temp parent, temp sibling anchor, 아직 생성 전 temp anchor, 잘못된 real/temp 혼합 anchor, parent/sibling 정합성 실패 시나리오를 서비스/통합 테스트로 고정했다.

## Step 5. BLOCK_DELETE 구현

- 변경 내용: transaction에 `BLOCK_DELETE`를 추가하고 `blockRef + version` 형태의 request shape를 고정했다.
- 구현 내용: transaction 서비스가 block 소속 문서와 version을 검증한 뒤 기존 block delete를 호출하도록 연결했다.
- 응답 확장: 삭제된 루트 block의 `blockId`, `deletedAt`을 applied operation 결과에 포함했다.
- 리팩터링: `deletedAt` 생성과 삭제 결과 반환 책임을 `BlockService.delete(...)`로 이동해 단건 delete API와 transaction delete가 같은 서비스 경로를 공유하도록 맞췄다.

## Step 6. BLOCK_MOVE 구현

- 변경 내용: transaction에 `BLOCK_MOVE`를 추가하고 `blockRef + version + parentRef/afterRef/beforeRef` 계약을 연결했다.
- 구현 내용: transaction 서비스가 기존 `BlockService.move(...)`를 재사용하고, temp 위치 ref를 같은 transaction 컨텍스트에서 실제 blockId로 해석한 뒤 이동 결과의 `version`, `sortKey`를 응답에 담도록 했다.
- 리팩터링: `BlockService.move(...)`를 `Block` 반환형으로 조정해 transaction 응답이 결과 상태를 재사용하도록 정리했다.

## Step 7. 검증, no-op, batch version 계약 보강

- 변경 내용: `DocumentTransactionServiceImplTest`, `DocumentTransactionApiIntegrationTest`, `DocumentControllerWebMvcTest`를 중심으로 temp ref 순서, unknown anchor, cross-document 참조, stale version, mixed batch edge case를 계속 보강했다.
- 변경 내용: `DocumentTransactionOperationStatus`에 `NO_OP`를 추가해 `BLOCK_MOVE`, `BLOCK_REPLACE_CONTENT`의 유효하지만 상태가 바뀌지 않는 요청을 `APPLIED`와 분리했다.
- 변경 내용: 같은 batch 안의 real block도 서버 내부 최신 version을 이어받아 처리하도록 transaction 컨텍스트를 확장했고, 같은 real block에 대해 다른 base version을 섞어 보내면 conflict로 실패하도록 고정했다.

## Step 8. delete 동시성, temp delete, mixed edge case 보강

- 변경 내용: `BLOCK_DELETE`와 단건 block delete API가 실제 삭제 시점까지 root version을 원자적으로 검증하도록 repository soft delete query의 where 절에 root block id/version을 함께 걸었다.
- 변경 내용: temp block delete도 version 없이 허용하도록 request shape를 완화하고, `create -> delete(temp)` 및 그 주변 mixed batch 시나리오를 처리하도록 조정했다.
- 테스트 보강: `create -> replace -> delete(temp)`, `create -> move -> delete(temp)`, temp delete 뒤 후속 replace/move, existing delete missing version, no-op 뒤 delete 시나리오를 추가했다.

## Step 9. 실제 경합 기반 동시성 통합 테스트 보강

- 변경 내용: `DocumentTransactionConcurrencyIntegrationTest`를 추가해 `CountDownLatch` 기반으로 같은 block의 `replace/replace`, `move/move`, `delete/delete`, `replace/move`, `replace/delete`, `move/delete`, `replace->move batch`와 단건 replace, 서로 다른 block replace 동시 요청을 실제 경합 상태로 검증했다.
- 보강 내용: soft delete bulk update가 version을 올리지 않으면 동시에 열린 update/move가 삭제된 row를 다시 되살릴 수 있는 경쟁 조건을 확인했고, delete query가 삭제되는 subtree의 version도 함께 증가시키도록 보강했다.

## Step 10. 관리자 단건 블록 API를 transaction 모델에 정렬

- 목적: 관리자 전용 단건 블록 API의 외부 엔드포인트는 유지하되, 내부 처리 방식과 request/response 계약을 기존 에디터 transaction 저장 모델에 맞춘다.
- 변경 내용: `AdminBlockController`의 admin block create/update/move/delete API가 `DocumentTransactionRequest`를 받고 `DocumentTransactionResponse`를 반환하도록 정리했다.
- 변경 내용: 각 admin API는 자기 역할에 맞는 단일 operation 하나만 허용하고, `blockId`가 있는 update/move/delete 요청은 path 값과 body `blockRef` 일치 여부를 검증하도록 맞췄다.

## Step 11. admin 전용 orchestration과 공통 execution 코어 분리

- 목적: block CRUD 책임과 transaction orchestration 책임이 섞이지 않도록 구조를 정리한다.
- 변경 내용: `blockId -> documentId` 해석, 단일 operation 검증, 문서 version 증가, 응답 조립은 `AdminBlockTransactionServiceImpl`이 담당하도록 분리했다.
- 변경 내용: `DocumentTransactionOperationExecutor`를 공통 bean으로 추가해 `DocumentTransactionServiceImpl`과 admin 서비스가 같은 create/replace/move/delete 실행 코어와 `flush` 타이밍을 공유하도록 맞췄다.
- 정리 결과: `BlockServiceImpl`은 block CRUD와 도메인 규칙 책임만 남기고, transaction orchestration은 document transaction 서비스와 admin 서비스가 담당하도록 재배치했다.

## Step 12. 요구사항과 회귀 테스트 동기화

- 변경 내용: `docs/REQUIREMENTS.md`와 `docs/explainers/editor-transaction-save-model.md`에 admin block API의 transaction 기반 계약을 반영했다.
- 테스트 변경: `AdminBlockControllerWebMvcTest`, `BlockApiIntegrationTest`, `DocumentTransactionServiceImplTest`, `BlockServiceImplTest`, `DocumentTransactionApiIntegrationTest`, `DocumentTransactionConcurrencyIntegrationTest`를 새 구조 기준으로 재검증했다.
- 테스트 결과: 관련 WebMvc, 서비스, 통합 테스트와 `./gradlew test`까지 통과했다.

## Step 13. create 초기 content 허용 방향 문서화

- 목적: 새 블록 생성 후 바로 입력하는 흔한 autosave 경로에서 `BLOCK_CREATE + BLOCK_REPLACE_CONTENT`를 항상 분리해야 하는지 다시 검토한다.
- 변경 내용: `docs/discussions/2026-04-01-block-create-initial-content-review.md`에서 `BLOCK_CREATE`의 선택적 초기 `content` 허용안을 검토하고, 채택 방향을 정리했다.
- 결정 기록: `docs/decisions/020-allow-optional-content-on-block-create-in-transactions.md`를 추가해 새 블록 생성 시 선택적 초기 content를 함께 저장할 수 있도록 ADR을 남겼다.
- 문서 보강: `docs/REQUIREMENTS.md`, `docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md`, `docs/explainers/editor-transaction-save-model.md`, `docs/guides/frontend-editor-transaction-implementation-guide.md`, `docs/guides/backend-editor-transaction-processing-guide.md`, `docs/decisions/014-adopt-transaction-centered-editor-save-model.md`를 새 계약 기준으로 갱신했다.
