# 2026-03-25 AdminBlock transaction alignment

- Step 1: `AdminBlockController`의 4개 admin block API path/method는 유지하고, request/response 계약과 내부 실행은 `POST /v1/documents/{documentId}/transactions`와 동일한 transaction 모델로 맞춘다.
- Step 2: update/move/delete 경로는 `blockId`로 대상 블록을 조회해 소속 `documentId`를 해석한 뒤 `DocumentTransactionService.apply(...)`로 위임한다.
- Step 3: 각 admin API는 자기 역할에 맞는 단일 operation 하나만 허용하고, `blockId`가 있는 경로는 path 값과 body `blockRef` 일치 여부를 검증한다.
- Step 4: `docs/REQUIREMENTS.md`에 admin block API의 transaction 기반 계약을 반영하고, admin block WebMvc/통합 테스트를 새 계약 기준으로 정리한다.
- Step 5: 컨트롤러 가독성 기준에 맞지 않는 얇은 helper(`applyTransaction`, `resolveDocumentId`)를 제거하고, 의미가 있는 `validate...` 메서드만 남긴다.
- Step 6: `AdminBlockController`는 request/response 매핑만 남기고, 단일 operation 검증과 `blockId -> documentId` 해석은 `DocumentTransactionServiceImpl`의 admin 전용 진입점으로 이동했다.
- Step 7: 컨트롤러는 `DocumentTransactionCommand`에서 `batchId`와 단일 operation만 꺼내 service의 `applyCreate/applyReplaceContent/applyMove/applyDelete`에 직접 넘기고, 실행 컨텍스트 초기화와 단일-operation 검증은 service 내부에 유지했다.
- Step 8: admin 전용 public API에서 `DocumentTransactionCommand` 오버로드를 제거하고 `batchId + DocumentTransactionOperationCommand` 시그니처만 남겼다. 관련 WebMvc, boot 통합, infrastructure 서비스 테스트까지 새 시그니처 기준으로 검증했다.
- Step 9: 단건 transaction 진입점과 실제 operation 실행 책임을 `BlockServiceImpl`로 이동하고, `DocumentTransactionServiceImpl`은 batch에서 `BlockService.applyOperation(...)`만 호출하도록 단순화했다. `DocumentTransactionContext`를 core에 추가해 단건/배치가 같은 block reference 컨텍스트를 공유하도록 정리했다.
- Step 10: `BlockServiceImpl`은 다시 block CRUD 책임만 남기고, admin 단건용 transaction 진입점은 `AdminBlockTransactionServiceImpl`로 분리했다. `DocumentTransactionServiceImpl`과 admin 서비스가 공통 bean `DocumentTransactionOperationExecutor`를 통해 같은 create/replace/move/delete 실행 코어와 `flush` 타이밍을 공유하도록 바꿔 `@Transactional` self-invocation 경고를 제거했다. `AdminBlockControllerWebMvcTest`, `BlockApiIntegrationTest`, `DocumentTransactionServiceImplTest`, `BlockServiceImplTest`까지 재검증했다.
