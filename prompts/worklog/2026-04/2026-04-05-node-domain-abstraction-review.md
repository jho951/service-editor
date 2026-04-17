# 2026-04-05 Node 도메인 공통화 설계 검토

## Step 1. `Node` 공통화 설계 검토 결과 정리

- 변경 내용: `Document`, `Block`, `DocumentTransactionServiceImpl`, `DocumentTransactionOperationExecutor`, `OrderedSortKeyGenerator`를 기준으로 공통성과 차이를 검토했고, Notion 공개 자료, ProseMirror 문서 구조, fractional-indexing 자료를 참고해 editor 모델과 서버 영속 모델의 경계를 비교했다.
- 판단: 현재 공통성은 영속 상태보다 operation 문맥에서 더 강하고, `Node` 단일 영속 엔티티나 `Node + FK` 절충안은 지금 기준에서 과하다고 정리했다. 현재 추천안은 `Node` 영속 통합이 아니라 `EditorOperationController` 축의 operation-level 공통화다.

## Step 2. `node-domain-abstraction` discussion 문서 최종 정리

- 변경 내용: `docs/discussions/2026-04-05-node-domain-abstraction-review.md`를 최종 기준으로 재정리했고, 문서 흐름을 `배경 -> 검토 범위 -> 핵심 질문 -> 검토한 선택지 -> 비교 요약 -> 왜 이 선을 그었는가 -> 현재 추천 방향` 기준으로 맞췄다. 각 선택지 안에서는 `개요`, `시나리오`, `장점`, `단점`, `판단`이 함께 읽히도록 구성했다.
- 변경 내용: `Node` hot table 우려, `Node + FK` 절충안 비효율, CRUD 전체 비통합 이유, 추천 시나리오와 예시, `Node.type` 선분기 반론까지 한 문서 안에서 이어지게 정리했다.
- 변경 내용: `배경`의 직접 근거 문서와 `관련 문서`의 후속 탐색 링크가 겹치지 않게 정리했고, 흐름 전환이 약한 지점에는 구분선 `---`을 제한적으로 반영했다.

## Step 3. `Node` discussion 문서 범위와 표현 정리

- 변경 내용: discussion 문서 안에서 `Node` 영속 통합 여부, operation-level 공통화 범위, FK 절충안 한계를 중심으로 읽히도록 논점을 정리했다.
- 판단: 설계 판단과 직접 연결되지 않는 일반 문서 체계 정리 내용은 분리 가능한 별도 작업으로 보고 이 로그 범위에서 제외했고, 이번 로그는 `Node` 도메인 공통화 설계 검토와 해당 discussion 문서 정리에만 초점을 맞추도록 정리했다.

## Step 4. `EditorOperationController` 채택과 구현 전 문서화

- 변경 내용: `Node` 영속 통합 대신 `EditorOperationController` 경계로 에디터 성격의 write API를 모으는 방향을 채택했고, 채택 기록은 `docs/decisions/021-adopt-editor-operation-controller-boundary.md`에 ADR로 남겼다. 현재 유효한 API 계약은 `docs/REQUIREMENTS.md`에 반영했고, 구현 전 참고용으로 `docs/guides/editor/editor-guideline.md`를 추가했다.
- 변경 내용: `docs/guides/editor/frontend-editor-guideline.md`, `docs/guides/editor/backend-editor-guideline.md`, `docs/explainers/editor-transaction-save-model.md`, 관련 topic 문서도 새 operation 경계에 맞게 갱신했다.
- 판단: `EditorOperationController`는 범용 `type` 분기 endpoint가 아니라 의미가 분명한 operation endpoint 묶음으로 두는 쪽을 채택했다. 우선 범위는 document save와 단일 move endpoint 2개로 좁혔고, save는 기존 transaction orchestration을 재사용하며, move는 `POST /editor-operations/move` 단일 진입점에서 `resourceType` 기반으로 문서/블록 이동을 함께 처리하고 drag 중간 상태를 저장하지 않는 explicit action API로 정리했다.

## Step 5. editor 공통 move API 구현

- 변경 내용: `EditorOperationController`를 추가하고 `POST /editor-operations/move`를 구현했다. 요청 DTO와 enum 이름은 `EditorMoveOperationRequest`, `EditorMoveResourceType`로 고정했다.
- 변경 내용: 새 move endpoint는 문서 이동일 때 `DocumentService.move(...)`, 블록 이동일 때 `BlockService.move(...)`를 직접 호출해 기존 알고리즘과 동작을 그대로 재사용하도록 연결했다.
- 변경 내용: 기존 `DocumentController`의 document move endpoint와 `AdminBlockController`의 block move endpoint는 즉시 제거하지 않고 deprecated 호환 경로로 남겼다.
- 변경 내용: editor guide 3문서 중 move 관련 기준도 현재 구현 상태에 맞게 보강했다.

## Step 6. editor save API 구조 확정

- 변경 내용: `EditorOperationController`에 `POST /editor-operations/documents/{documentId}/save`를 추가했다.
- 변경 내용: save는 외부 path만 새 editor 경계로 옮기는 수준에서 먼저 시작했다.
- 변경 내용: 기존 `DocumentController`의 `POST /documents/{documentId}/transactions`는 즉시 제거하지 않고 deprecated 호환 경로로 남겼다.

## Step 7. editor 공통 orchestrator 기준 재정리

- 변경 내용: `EditorOperationController` 아래의 공통 application 계층을 `EditorOperationOrchestrator` 하나로 두는 방향으로 기준을 다시 고정했다.
- 변경 내용: move도 `EditorOperationOrchestrator.move(...)`를 타되, orchestrator 안에서 document direct 처리와 block executor 재사용으로 분기하는 방향으로 선을 다시 그었다.
- 변경 내용: save public 경계는 `EditorSaveRequest`, `EditorSaveResponse`, `EditorSaveApiMapper` 기준으로 재정리했고, 기존 `DocumentTransaction*` public 경계는 deprecated 브리지로 남기기로 했다.
- 판단: 공통 controller를 두더라도 save와 move를 같은 방식으로 실행할 필요는 없다. 현재 구조에서는 둘 다 orchestrator를 진입점으로 두되, save는 batch orchestration, move는 resourceType 분기 처리로 책임을 나누는 편이 더 일관되다.

## Step 8. save 내부 구조를 editor 중심으로 전환

- 변경 내용: `EditorOperationOrchestrator.save(...)`가 더 이상 `DocumentTransactionService` 브리지에 의존하지 않고 직접 저장 흐름을 수행하도록 바꿨다.
- 변경 내용: `EditorSaveOperationType`, `EditorSaveContext`, `EditorSaveOperationStatus`, `EditorSaveOperationExecutor`를 추가해 save 내부 구조도 `EditorSave*` family 기준으로 맞췄다.
- 변경 내용: `EditorSaveCommand` 안의 operation type도 `DocumentTransactionOperationType`이 아니라 `EditorSaveOperationType`을 사용하도록 바꿨다.
- 판단: editor 통합 구조를 채택한 이상 public 경계만 `EditorSave*`로 바꾸고 내부 중심을 계속 `DocumentTransaction*`로 두는 방식은 구조 일관성을 해친다. save 알고리즘은 유지하되, editor 경계에서 보이는 타입과 실행 구조 모두 `EditorSave*`로 통일하는 편이 맞다.

## Step 9. admin block 단건 API 내부 타입을 `EditorSave*` 기준으로 전환

- 변경 내용: `AdminBlockController`의 create/update/delete/move 4개 단건 API는 `EditorSaveRequest`, `EditorSaveResponse`, `EditorSaveApiMapper`를 사용하도록 바꿨다.
- 변경 내용: `AdminBlockOperationService`, `AdminBlockOperationServiceImpl`는 유지하되, 내부에서 사용하던 `DocumentTransactionOperationCommand`, `DocumentTransactionResult`, `DocumentTransactionOperationExecutor`를 `EditorSaveOperationCommand`, `EditorSaveResult`, `EditorSaveOperationExecutor` 기준으로 치환했다.
- 변경 내용: admin block 단건 경로의 검증/조회/문서 version 증가 순서는 기존 `AdminBlockOperationServiceImpl` 역할을 유지하도록 두었고, 관련 테스트 명명과 `docs/REQUIREMENTS.md`도 `DocumentTransaction*` 기준에서 `EditorSave*` 기준으로 맞췄다.
- 판단: admin block API의 역할은 save 4개 operation을 운영/관리 경로에서 단건으로 치기 쉽게 만든 보조 경계다. 따라서 controller와 request/response 명명은 `EditorSave*`로 맞추되, 기존 단건 wrapper의 동작 순서와 책임은 `AdminBlockOperationServiceImpl` 안에 유지하는 편이 기존 동작 치환에 더 맞다.

## Step 10. deprecated transaction legacy 경로 제거

- 변경 내용: `DocumentController`에서 deprecated `POST /documents/{documentId}/transactions`, `POST /documents/{documentId}/move` 경로를 제거했다.
- 변경 내용: 더 이상 참조되지 않는 `DocumentTransaction*` DTO, mapper, service, executor, transaction package와 `MoveDocumentRequest`를 삭제했다.
- 변경 내용: legacy save/move 테스트는 제거하고, 현재 계약 검증은 `EditorOperationControllerWebMvcTest`, `EditorOperationApiIntegrationTest`, `EditorOperationConcurrencyIntegrationTest`, `EditorOperationOrchestratorImplTest` 기준으로 유지하도록 정리했다.
- 판단: deprecated save/move 경로의 기능과 핵심 오류 시나리오는 editor 경계 테스트로 이미 옮겨졌고, admin block 단건 경로도 `EditorSave*` 기준으로 정리된 상태라 legacy transaction 축을 더 유지할 이유가 없다.

## Step 11. save 경계와 document version 책임 최종 정리

- 변경 내용: `EditorOperationOrchestratorImpl`, `AdminBlockOperationServiceImpl`, `EditorSaveOperationExecutor`에서 `DocumentRepository`, `BlockRepository` 직접 참조를 제거했다.
- 변경 내용: `BlockServiceImpl`, `DocumentServiceImpl`의 기본 write 메서드에서는 명시적 `flush()`를 제거하고, 최신 block version 또는 save/move 응답의 최신 block 상태가 필요한 지점에서만 `PersistenceContextManager`를 통해 flush 하도록 정리했다.
- 변경 내용: 문서 version 증가 책임은 한때 `DocumentService.incrementVersion(...)`로 올렸지만, 최종적으로는 `DocumentVersionUpdater`, `DocumentVersionUpdaterImpl`로 다시 분리했다.
- 변경 내용: `BlockServiceImpl`, `EditorOperationOrchestratorImpl`, `AdminBlockOperationServiceImpl`는 더 이상 `DocumentService`를 통해 문서 version 증가를 우회하지 않고, 목적이 드러나는 전용 updater를 사용하도록 정리했다.
- 판단: 오케스트레이터와 executor는 유스케이스 조정과 save 알고리즘 해석에 집중하고, 영속화 시점은 필요한 지점에서만 orchestration 경계가 flush 하도록 두는 편이 경계가 더 선명하다.
- 판단: block version/sortKey는 더티체킹과 선택적 flush로 충분하지만, document version까지 `@Version` dirty-checking 증가나 일반 `DocumentService` 책임으로 두면 동시성 정책 의도가 흐려진다. 이 저장소의 요구사항과 통합 테스트 기준에서는 bulk 증가 정책을 전용 updater로 분리해 유지하는 쪽이 맞다.
- 검증: `BlockServiceImplTest`, `DocumentServiceImplTest`, `EditorOperationOrchestratorImplTest`, `AdminBlockOperationServiceImplTest`, `EditorOperationControllerWebMvcTest`, `AdminBlockControllerWebMvcTest`, `DocumentControllerWebMvcTest`, `EditorOperationApiIntegrationTest`, `EditorOperationConcurrencyIntegrationTest`, `AdminBlockApiIntegrationTest`, `DocumentBlocksApiIntegrationTest`, `DocumentApiIntegrationTest`
- 검증: `:documents-infrastructure:test --tests com.documents.service.AdminBlockOperationServiceImplTest --tests com.documents.service.EditorOperationOrchestratorImplTest --tests com.documents.service.BlockServiceImplTest`
- 검증: `:documents-infrastructure:test --tests com.documents.service.DocumentServiceImplTest`
- 검증: `:documents-api:test --tests com.documents.api.editor.EditorOperationControllerWebMvcTest --tests com.documents.api.block.AdminBlockControllerWebMvcTest --tests com.documents.api.document.DocumentControllerWebMvcTest`
- 검증: `:documents-boot:test --tests com.documents.api.editor.EditorOperationApiIntegrationTest --tests com.documents.api.editor.EditorOperationConcurrencyIntegrationTest --tests com.documents.api.block.AdminBlockApiIntegrationTest --tests com.documents.api.block.DocumentBlocksApiIntegrationTest --tests com.documents.api.document.DocumentApiIntegrationTest`

## Step 12. editor 문서 기준 최종 정리

- 변경 내용: `docs/REQUIREMENTS.md`의 admin block 보조 API 계약을 현재 구현 기준인 `EditorSave*` 단건 모델로 최종 반영했다.
- 변경 내용: `docs/guides/editor/` 문서군에서는 save와 move의 서비스 경계를 다시 맞췄다. save와 move 모두 `EditorOperationOrchestrator`를 진입점으로 사용하고, move는 orchestrator 안에서 document direct 처리와 block executor 재사용으로 분기하는 현재 구조로 정리했다.
- 변경 내용: `docs/explainers/editor-save-model.md`, `docs/guides/editor/frontend-editor-guideline.md`에 남아 있던 예전 단건 block 보조 API 경로를 `/admin/**` 기준으로 정리했다.
- 판단: `REQUIREMENTS`, guide, explainer는 현재 기준을 한 번에 읽히게 통합하는 문서라 최신 구조로 덮어써야 하고, worklog만 어떤 판단 변경이 있었는지 Step으로 누적하는 편이 문서 성격에 맞다.
