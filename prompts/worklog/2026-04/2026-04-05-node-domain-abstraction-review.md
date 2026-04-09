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
- 변경 내용: 공통 orchestrator의 실제 도입 범위는 save로 한정하고, move는 기존 구현 경계를 유지하는 방향으로 선을 다시 그었다.
- 변경 내용: save public 경계는 `EditorSaveRequest`, `EditorSaveResponse`, `EditorSaveApiMapper` 기준으로 재정리했고, 기존 `DocumentTransaction*` public 경계는 deprecated 브리지로 남기기로 했다.
- 판단: 공통 controller를 두더라도 모든 operation을 즉시 같은 orchestrator에 넣을 필요는 없다. 현재 단계에서는 save의 public 경계와 내부 구조를 먼저 editor family 기준으로 고정하는 편이 더 일관되다.

## Step 8. save 내부 구조를 editor 중심으로 전환

- 변경 내용: `EditorOperationOrchestrator.save(...)`가 더 이상 `DocumentTransactionService` 브리지에 의존하지 않고 직접 저장 흐름을 수행하도록 바꿨다.
- 변경 내용: `EditorSaveOperationType`, `EditorSaveContext`, `EditorSaveOperationStatus`, `EditorSaveOperationExecutor`를 추가해 save 내부 구조도 `EditorSave*` family 기준으로 맞췄다.
- 변경 내용: `EditorSaveCommand` 안의 operation type도 `DocumentTransactionOperationType`이 아니라 `EditorSaveOperationType`을 사용하도록 바꿨다.
- 판단: editor 통합 구조를 채택한 이상 public 경계만 `EditorSave*`로 바꾸고 내부 중심을 계속 `DocumentTransaction*`로 두는 방식은 구조 일관성을 해친다. save 알고리즘은 유지하되, editor 경계에서 보이는 타입과 실행 구조 모두 `EditorSave*`로 통일하는 편이 맞다.

## Step 9. move orchestrator 범위 보류

- 변경 내용: 이번 작업 범위를 save 중심 리팩토링으로 한정하기 위해, move는 `EditorOperationOrchestrator` 범위에서 제외하고 controller가 기존 문서/블록 이동 서비스로 직접 연결하도록 정리했다.
- 판단: save와 move를 최종적으로 같은 editor orchestration family로 맞출 수는 있지만, 이번 변경에서는 save 구조를 먼저 고정하는 것이 우선이다. move까지 동시에 편입하면 범위가 커져 구조 판단과 회귀 검증이 섞인다.
