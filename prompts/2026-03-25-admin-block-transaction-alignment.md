# 2026-03-25 AdminBlock transaction alignment

- 작업 목적: 관리자 전용 단건 블록 API의 외부 엔드포인트는 유지하면서, 내부 처리 방식과 request/response 계약을 기존 에디터 저장 transaction 모델에 맞추도록 정리한다.
- 관련 요구사항: `docs/REQUIREMENTS.md`
- 관련 explainer: `docs/explainers/editor-transaction-save-model.md`

## Step 1. 관리자 단건 블록 API를 transaction 계약으로 전환

- `AdminBlockController`의 4개 admin block API는 유지하고, request/response 계약은 `POST /v1/documents/{documentId}/transactions`와 같은 transaction 모델로 맞췄다.
- create/update/move/delete는 모두 `DocumentTransactionRequest`를 받고 `DocumentTransactionResponse`를 반환하도록 정리했다.

## Step 2. 단건 요청 제약과 block 기준 검증 정리

- 각 admin API는 자기 역할에 맞는 단일 operation 하나만 허용하도록 정리했다.
- `blockId`가 있는 update/move/delete 요청은 path 값과 body `blockRef` 일치 여부를 검증하도록 맞췄다.

## Step 3. 컨트롤러 책임을 request/response 매핑으로 축소

- 컨트롤러는 `DocumentTransactionCommand`에서 `batchId`와 단일 operation만 꺼내 service의 `applyCreate/applyReplaceContent/applyMove/applyDelete`에 직접 넘기도록 정리했다.
- 컨트롤러 가독성 기준에 맞지 않는 얇은 helper(`applyTransaction`, `resolveDocumentId`)는 제거하고, HTTP 계층에는 request/response 매핑만 남겼다.

## Step 4. admin 전용 orchestration 계층 추가

- update/move/delete는 `blockId`로 대상 블록을 조회해 소속 `documentId`를 해석한 뒤 공통 실행 코어로 위임하도록 흐름을 바꿨다.
- 단일 operation 검증, `blockId -> documentId` 해석, 문서 version 증가, 응답 조립은 `AdminBlockTransactionServiceImpl`에서 담당하도록 분리했다.
- admin 전용 public API는 `DocumentTransactionCommand` 오버로드를 제거하고 `batchId + DocumentTransactionOperationCommand` 시그니처만 남겼다.

## Step 5. 공통 execution 코어 분리

- 단건 transaction 진입점과 실제 operation 실행 책임을 분리하기 위해 `DocumentTransactionOperationExecutor`를 공통 bean으로 추가했다.
- `DocumentTransactionServiceImpl`과 admin 서비스가 같은 create/replace/move/delete 실행 코어와 `flush` 타이밍을 공유하도록 정리했다.
- `DocumentTransactionContext`를 core에 추가해 단건/배치가 같은 block reference 컨텍스트를 공유하도록 맞췄다.

## Step 6. block 서비스 책임 재정리

- 단건 transaction 진입점을 한때 `BlockServiceImpl`로 옮겼다가, block CRUD 책임과 orchestration 책임이 섞이지 않도록 admin 단건용 transaction 진입점을 `AdminBlockTransactionServiceImpl`로 다시 분리했다.
- 결과적으로 `BlockServiceImpl`은 block CRUD와 도메인 규칙 수행 책임만 남기고, transaction orchestration은 admin 서비스와 document transaction 서비스가 담당하도록 정리했다.
- 이 구조로 `@Transactional` self-invocation 경고가 생기지 않도록 정리했다.

## Step 7. 요구사항과 테스트 정리

- `docs/REQUIREMENTS.md`에 admin block API의 transaction 기반 계약을 반영했다.
- `AdminBlockControllerWebMvcTest`, `BlockApiIntegrationTest`, `DocumentTransactionServiceImplTest`, `BlockServiceImplTest`를 새 구조 기준으로 정리하고 재검증했다.

## 테스트 실행 결과

- `./gradlew :documents-api:test --tests com.documents.api.block.AdminBlockControllerWebMvcTest --tests com.documents.api.document.DocumentControllerWebMvcTest` 통과
- `./gradlew :documents-infrastructure:test --tests com.documents.service.AdminBlockTransactionServiceImplTest --tests com.documents.service.DocumentTransactionServiceImplTest --tests com.documents.service.BlockServiceImplTest` 통과
- `./gradlew :documents-boot:test --tests com.documents.api.block.BlockApiIntegrationTest --tests com.documents.api.document.DocumentTransactionApiIntegrationTest --tests com.documents.api.document.DocumentTransactionConcurrencyIntegrationTest` 통과
- `./gradlew test` 통과

## 변경 경로

- 요구사항: `docs/REQUIREMENTS.md`
- 설명 문서: `docs/explainers/editor-transaction-save-model.md`
