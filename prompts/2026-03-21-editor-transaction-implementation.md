# 2026-03-21 editor transaction implementation

## Step 1. create + replace_content transaction 최소 경로 구현

- `POST /v1/documents/{documentId}/transactions` 엔드포인트와 request/response DTO를 추가했다.
- v1 첫 구현 범위는 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT` 두 operation만 처리하도록 두고, `tempId -> real blockId` 매핑과 applied operation 응답을 반환하도록 연결했다.
- `documents-core`에 transaction command/result 모델과 서비스 인터페이스를 추가하고, `documents-infrastructure`에 transaction orchestration 구현을 추가했다.
- 같은 batch 안의 `tempId` 대상 `replace_content`는 transaction 컨텍스트에서 실제 `blockId`와 version으로 해석하도록 구현했다.
- `BlockContentValidator`는 null 허용으로 조정하고, 필수 여부는 각 요청 DTO가 맡도록 바꿔 nested transaction DTO 검증과 기존 block API 검증을 함께 맞췄다.
- 검증은 `:documents-api:test --tests 'com.documents.api.document.DocumentControllerWebMvcTest' -x :documents-boot:test` 와 `:documents-infrastructure:test --tests 'com.documents.service.DocumentTransactionServiceImplTest' -x :documents-boot:test`로 확인했다.
- 의도가 숨는 조건문은 인라인으로 두지 않고, `validateTempIdIsUnique`, `registerTempBlockContext`, `isTempBlockReference` 같은 이름 있는 메서드로 정리하는 기준을 현재 구현에 반영했다.

## Step 2. request contract를 blockRef 하나로 통일

- transaction request의 블록 참조 필드는 operation 종류와 무관하게 `blockRef` 하나로 통일했다.
- `BLOCK_CREATE`도 별도 `tempId` 필드를 쓰지 않고, `blockRef`에 새 block용 `tempId`를 담도록 DTO, 서비스, 테스트를 맞췄다.
- 성공 응답은 create 결과에 한해 `tempId + blockId`를 함께 내려 프론트가 로컬 참조를 실제 ID로 치환할 수 있게 유지했다.
- 요구사항, ADR, explainer, frontend/backend guide, discussion 문서의 request 예시와 설명도 같은 기준으로 정리했다.

## Step 3. transaction 실패 경로 테스트 보강

- WebMvc 테스트에 `BLOCK_CREATE`/`BLOCK_REPLACE_CONTENT`의 잘못된 shape 조합, 서비스 레벨 `INVALID_REQUEST`, `CONFLICT` 응답 매핑을 추가했다.
- 서비스 테스트에 중복 `blockRef` create, unknown temp ref, 잘못된 real `blockRef`, existing block의 missing version, create 후 replace conflict 전파를 추가했다.
- boot 통합 테스트에 create 뒤 기존 block 충돌이 나면 앞서 insert한 새 block까지 rollback되는 시나리오를 추가했다.
- 검증은 `:documents-api:test --tests 'com.documents.api.document.DocumentControllerWebMvcTest' -x :documents-boot:test`, `:documents-infrastructure:test --tests 'com.documents.service.DocumentTransactionServiceImplTest' -x :documents-boot:test`, `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionApiIntegrationTest'`로 확인했다.

## Step 4. 위치 참조를 ref 모델로 재정의

- `blockRef`만 temp를 지원하면 부모-자식 생성과 같은 batch 내 sibling 기준 삽입이 막히므로, transaction 위치 참조도 `parentRef`, `afterRef`, `beforeRef`로 통일하기로 정리했다.
- v1은 temp parent와 temp sibling anchor까지 지원하는 방향으로 요구사항, ADR, explainer, frontend/backend guide, discussion 문서를 갱신했다.
- 서버는 request 순서대로 `tempId -> real blockId` 매핑 컨텍스트를 갱신하면서 `blockRef`, `parentRef`, `afterRef`, `beforeRef`를 모두 해석해야 한다는 구현 기준을 문서에 명시했다.

## Step 5. temp 위치 ref 해석 구현

- transaction DTO와 command의 위치 필드를 `parentRef`, `afterRef`, `beforeRef`로 바꾸고, `BLOCK_REPLACE_CONTENT`에는 위치 ref가 오지 못하도록 validation을 유지했다.
- transaction service는 create 시 `parentRef`, `afterRef`, `beforeRef`도 `tempId -> real blockId` 컨텍스트로 해석한 뒤 기존 `BlockService.create(...)`에 실제 UUID를 넘기도록 확장했다.
- 서비스 테스트에 temp parent, temp afterRef/beforeRef 해석 시나리오를 추가했고, boot 통합 테스트에도 temp parent와 temp sibling anchor를 실제 저장 순서/계층 반영까지 검증하는 시나리오를 추가했다.
- 검증은 `:documents-api:test --tests 'com.documents.api.document.DocumentControllerWebMvcTest' -x :documents-boot:test`, `:documents-infrastructure:cleanTest :documents-infrastructure:test --tests 'com.documents.service.DocumentTransactionServiceImplTest' -x :documents-boot:test`, `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionApiIntegrationTest'`로 확인했다.

## Step 6. temp ref edge case 테스트 보강

- 존재하지 않는 temp `parentRef`, `afterRef`, `beforeRef`, 아직 생성 전 temp anchor 참조를 서비스/통합 테스트에서 `INVALID_REQUEST`로 고정했다.
- 대상 parent의 sibling이 아닌 anchor를 create에 섞어 쓰는 경우와 real `afterRef` + temp `beforeRef` 혼합 anchor 해석도 테스트로 고정했다.
- temp ref를 허용하더라도 request 순서와 sibling/parent 정합성을 반드시 지켜야 한다는 점을 현재 테스트 세트로 보강했다.
- 검증은 `:documents-infrastructure:cleanTest :documents-infrastructure:test --tests 'com.documents.service.DocumentTransactionServiceImplTest' -x :documents-boot:test`, `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionApiIntegrationTest'`로 확인했다.

## Step 7. block_delete transaction 구현

- 문서/사이드바 CRUD가 아니라 에디터 블록 batch 저장이라는 기존 설계를 다시 확인하고, transaction에는 `BLOCK_DELETE`만 추가했다.
- `BLOCK_DELETE`는 `blockRef + version`만 받도록 request shape을 고정하고, transaction 서비스에서 block 소속 문서와 version을 검증한 뒤 기존 block delete를 호출하도록 연결했다.
- delete 응답에는 삭제된 루트 block의 `blockId`, `deletedAt`을 포함하도록 applied operation 결과 모델을 확장했다.
- 서비스/WebMvc/boot 통합 테스트에 subtree soft delete, 다른 문서 block 거절, stale version conflict, delete 실패 시 전체 rollback 시나리오를 추가했다.

## Step 8. block delete 결과 반환 책임을 BlockService로 이동

- `deletedAt` 생성과 삭제 결과 반환 책임은 transaction 서비스가 아니라 `BlockService.delete(...)`가 가져야 한다고 정리하고, 반환형을 `Block`으로 변경했다.
- `BlockService.delete(...)`는 삭제 전에 조회한 루트 block에 자신이 만든 `deletedAt`과 `updatedBy`를 반영해 그대로 반환하고, 추가 PK 재조회 없이 delete 결과를 응답에 쓸 수 있게 바꿨다.
- `DocumentTransactionServiceImpl`은 `BlockService.delete(...)`가 돌려준 삭제된 루트 block에서 `deletedAt`을 응답에 사용하도록 단순화했다.
- 단건 block delete API와 transaction delete가 같은 서비스 delete 경로를 그대로 공유하도록 맞췄다.
- 검증은 `:documents-infrastructure:test --tests 'com.documents.service.BlockServiceImplTest' --tests 'com.documents.service.DocumentTransactionServiceImplTest'`, `:documents-api:test --tests 'com.documents.api.block.BlockControllerWebMvcTest' --tests 'com.documents.api.document.DocumentControllerWebMvcTest'`, `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionApiIntegrationTest' --tests 'com.documents.api.block.BlockApiIntegrationTest'`로 확인했다.

## Step 9. block_move transaction 구현

- 기존 문서 계약을 다시 확인하고, `transactions`에는 문서/사이드바 CRUD가 아니라 블록 저장 operation만 들어간다는 기준을 유지한 채 `BLOCK_MOVE`를 추가했다.
- `BLOCK_MOVE`는 `blockRef + version + parentRef/afterRef/beforeRef` 조합을 사용하고, `content`는 허용하지 않도록 request shape을 확장했다.
- transaction 서비스는 기존 `BlockService.move(...)`를 재사용하고, temp 위치 ref를 같은 transaction 컨텍스트에서 실제 blockId로 해석한 뒤 이동 결과의 `version`, `sortKey`를 응답에 담도록 연결했다.
- `BlockService.move(...)`는 transaction 응답이 결과 상태를 재사용할 수 있도록 `Block` 반환형으로 조정했고, 단건 move API는 기존처럼 반환값을 무시한다.
- 문서 본문 요구사항과 guide는 이미 `BLOCK_MOVE` 계약을 포함하고 있어 추가 수정은 하지 않고, 서비스/WebMvc/boot 테스트와 작업 로그만 갱신했다.

## Step 10. transaction 검증 테스트 보강

- `DocumentTransactionServiceImpl`의 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE` 전반에 대해 temp ref 순서, unknown temp anchor, temp block move 후 version 전파, stale version, cross-document 참조를 더 촘촘히 검증하는 서비스 테스트를 추가했다.
- 테스트 보강 과정에서 `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`가 요청 `documentId` 소속이 아닌 real blockRef를 통과시키는 구멍을 확인했고, 두 경로도 `resolveExistingBlock(...)`를 거쳐 문서 소속과 version을 함께 검증하도록 보정했다.
- boot 통합 테스트에는 temp block move 후 replace 누적 반영, temp block delete 거절 및 rollback, move/replace의 다른 문서 블록 참조 거절, move no-op version 유지, unknown/future temp anchor 거절 시나리오를 추가했다.
- 검증은 `:documents-infrastructure:test --tests 'com.documents.service.DocumentTransactionServiceImplTest'`, `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionApiIntegrationTest'`, `:documents-api:test --tests 'com.documents.api.document.DocumentControllerWebMvcTest'`로 확인했다.

## Step 11. transaction no-op status 분리

- `BLOCK_MOVE`는 요청이 유효하지만 실제 parent/sortKey 변화가 없는 경우, `BLOCK_REPLACE_CONTENT`는 현재 저장 문자열과 요청 문자열이 같은 경우를 no-op으로 보고 `APPLIED`와 구분해 응답하도록 `DocumentTransactionOperationStatus`에 `NO_OP`를 추가했다.
- transaction request의 `content`는 이미 `BlockJsonCodec.write(...)`를 거쳐 저장 문자열 포맷으로 정규화되므로, `BlockService.update(...)`는 동일 문자열이면 no-op으로 바로 반환하고 `updatedBy`와 version을 유지하도록 조정했다.
- `DocumentTransactionServiceImpl`는 move/replace 공통 no-op 판정 규칙을 `resolveAppliedStatus(...)` 메서드로 올리고, operation type 기준 `switch`에서 현재는 `BLOCK_MOVE`, `BLOCK_REPLACE_CONTENT`만 `NO_OP` 후보로 처리하고 `BLOCK_CREATE`, `BLOCK_DELETE`는 항상 `APPLIED`로 고정했다.
- 서비스 테스트, WebMvc 테스트, boot 통합 테스트에 move/replace no-op status, version 유지, replace 시 updatedBy 유지 검증을 추가했다.
- 검증은 `:documents-infrastructure:test --tests 'com.documents.service.BlockServiceImplTest' --tests 'com.documents.service.DocumentTransactionServiceImplTest'`, `:documents-api:test --tests 'com.documents.api.document.DocumentControllerWebMvcTest'`, `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionApiIntegrationTest'`로 확인했다.

## Step 12. transaction edge case 테스트 보강

- 추가 케이스: move no-op 뒤 replace, replace no-op 뒤 move, temp block 연속 move, `create -> replace -> move -> replace -> move` mixed batch, delete 뒤 후속 replace/move, subtree delete 뒤 자식 block 후속 replace, real block 연속 stale version, move self-anchor, move reversed anchor, move same-anchor(`afterRef == beforeRef`), target parent와 맞지 않는 afterRef/beforeRef.
- 검증은 `:documents-infrastructure:test --tests 'com.documents.service.DocumentTransactionServiceImplTest'`, `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionApiIntegrationTest'`로 확인했다.

## Step 13. real block batch version 계약 정리

- temp block뿐 아니라 기존 서버 block도 같은 batch 안에서는 서버가 내부 최신 version을 이어받아 처리하도록 transaction 컨텍스트를 확장했다.
- 프론트 계약은 `BLOCK_CREATE`와 temp 대상 `BLOCK_REPLACE_CONTENT`/`BLOCK_MOVE`는 version 없이 보내고, 기존 서버 block 대상 `BLOCK_REPLACE_CONTENT`/`BLOCK_MOVE`/`BLOCK_DELETE`는 batch 생성 시점의 base version을 보낸 뒤 같은 block의 후속 op에도 같은 base version을 유지하는 것으로 정리했다.
- 같은 real block에 대해 batch 안에서 서로 다른 base version을 섞어 보내면 conflict로 실패하도록 검증했고, `replace -> move -> replace`, `replace -> move -> delete`, existing block의 missing version 같은 시나리오 테스트를 추가했다.
- 프론트/백엔드 guide와 save model explainer도 같은 계약으로 갱신했다.

## Step 14. block delete optimistic concurrency 보강

- `BLOCK_DELETE`와 단건 block delete API는 pre-check만 하고 bulk soft delete를 실행하던 구조라, 검증 직후 다른 사용자가 root block을 먼저 수정해도 stale delete가 통과할 수 있었다.
- `BlockService.delete(...)`에 version을 올리고, repository soft delete query도 root block id/version을 where 절에 함께 걸어 실제 삭제 시점까지 root version을 원자적으로 검증하도록 바꿨다.
- transaction delete는 내부 current version을, 단건 block delete API는 request query param `version`을 전달하도록 맞췄다.
- 서비스/WebMvc/boot 통합 테스트에 delete version 전달, stale delete conflict, 기존 delete 응답 경로를 다시 검증했다.

## Step 15. temp block delete 허용

- 기존에는 프론트 queue가 `create -> ... -> delete`를 flush 전에 collapse한다고 보고 temp block delete를 request shape에서 사실상 막고 있었다.
- 하지만 collapse 실패나 경계 케이스까지 고려하면 temp delete를 전체 batch 에러로 터뜨리기보다, version 없이 자연스럽게 처리하는 쪽이 더 안전하다고 보고 `BLOCK_DELETE` request shape를 완화했다.
- transaction 서비스는 기존 temp context/currentVersion 흐름을 그대로 사용해 create 직후의 temp block delete를 처리하고, real block delete만 base version을 요구하도록 유지했다.
- 서비스/WebMvc/boot 통합 테스트에 `create -> delete(temp)` 성공 시나리오와 관련 request shape 통과를 추가했고, guide/explainer도 temp delete 허용 기준으로 갱신했다.

## Step 16. transaction delete/no-op mixed edge case 보강

- temp delete 허용 이후의 경계를 다시 점검해 `create -> replace -> delete(temp)`, `create -> move -> delete(temp)`, temp delete에 version 포함, existing delete missing version, temp delete 뒤 후속 replace/move, replace no-op 뒤 delete, move no-op 뒤 delete 시나리오를 추가로 고정했다.
- 서비스 테스트는 currentVersion 전달과 예외 종류를, boot 통합 테스트는 실제 rollback과 최종 DB 상태를 함께 검증하도록 보강했다.
- 검증은 `:documents-infrastructure:test --tests 'com.documents.service.DocumentTransactionServiceImplTest'`, `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionApiIntegrationTest'`로 확인했다.

## Step 17. transaction 동시성 통합 검증 보강

- `DocumentTransactionConcurrencyIntegrationTest`를 추가해 `CountDownLatch` 기반으로 같은 block의 `replace/replace`, `move/move`, `delete/delete`, `replace/move`, `replace/delete`, `move/delete`, `replace->move batch`와 단건 replace, 서로 다른 block replace 동시 요청을 실제 경합 상태로 검증했다.
- 동시성 테스트 과정에서 soft delete bulk update가 version을 올리지 않으면 동시에 열린 update/move가 삭제된 row의 `deletedAt`을 다시 덮어써 block이 되살아날 수 있는 경쟁 조건을 확인했고, delete query가 삭제되는 subtree의 version도 함께 증가시키도록 보강했다.
- API 레이어는 optimistic lock 예외 체인도 `409 CONFLICT`로 응답하도록 정리해 실제 경합에서 수동 conflict와 JPA conflict가 같은 계약으로 내려가게 맞췄다.
- 검증은 `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionConcurrencyIntegrationTest'`, `:documents-infrastructure:test --tests 'com.documents.service.BlockServiceImplTest' --tests 'com.documents.service.DocumentTransactionServiceImplTest'`, `:documents-api:test --tests 'com.documents.api.block.BlockControllerWebMvcTest' --tests 'com.documents.api.document.DocumentControllerWebMvcTest'`, `:documents-boot:test --tests 'com.documents.api.block.BlockApiIntegrationTest' --tests 'com.documents.api.document.DocumentTransactionApiIntegrationTest' --tests 'com.documents.api.document.DocumentTransactionConcurrencyIntegrationTest'`로 확인했다.

## Step 18. documentVersion 선검증과 응답 반환 추가

- transaction request top-level에 `documentVersion`을 추가하고, `DocumentTransactionServiceImpl`이 block 단위 version 검사 전에 현재 `Document.version`과 먼저 비교하도록 바꿨다.
- batch 안에 실제 editor 변경이 하나라도 적용되면 `documents.version`을 정확히 1 증가시키고, transaction 응답에도 최신 `documentVersion`을 함께 포함하도록 확장했다.
- `BLOCK_MOVE`, `BLOCK_REPLACE_CONTENT`가 모두 no-op인 batch는 기존처럼 block version과 함께 `documentVersion`도 올리지 않도록 유지했다.
- service/WebMvc/boot 통합 테스트와 동시성 테스트를 새 문서 version 계약 기준으로 갱신했다.

## Step 19. transaction create 경로의 document 중복 조회 제거

- `DocumentTransactionServiceImpl`은 batch 시작 시 조회한 `Document`를 `BLOCK_CREATE` 경로까지 그대로 전달하고, `BlockService`에는 `create(Document, ...)` 오버로드를 추가해 transaction 안에서 같은 문서를 다시 조회하지 않도록 정리했다.
- 기존 단건 block create API는 그대로 `create(UUID documentId, ...)`를 사용하고, 내부에서만 새 오버로드로 위임하도록 유지해 외부 호출부 가독성과 기존 테스트 계약은 유지했다.
- service 테스트에는 transaction create 시 `documentRepository.findByIdAndDeletedAtIsNull(...)`가 한 번만 호출되는 검증을 추가했다.

## Step 20. transaction 동시성 테스트 확장

- `DocumentTransactionConcurrencyIntegrationTest`를 같은 block 경쟁만 보는 수준에서 넓혀, 서로 다른 block `move/delete`, `create`, `create vs replace`, 같은 block 3-way replace, 서로 다른 block 3-way replace 시나리오를 추가했다.
- `serializingAnswer(...)` 기반의 결정적 경합 테스트로는 같은 변경 메서드 지점까지 동시에 들어온 뒤 하나만 먼저 실행되는 상황을 고정하고, `documentVersion` 정책 때문에 "같은 documentVersion이면 block이 달라도 하나만 성공"하는 규칙과 create가 끼어도 commit/rollback 경계가 깨지지 않는지까지 검증했다.
- 별도로 `CountDownLatch ready/start` 기반 경쟁형 헬퍼를 추가해 호출 순서를 통제하지 않고 요청 10개를 실제로 동시에 발사하는 테스트도 넣었다.
- 경쟁형 시나리오는 같은 block `replace_content` 10개, 같은 block `move` 10개, 같은 block `delete` 10개, 서로 다른 block `replace_content` 10개, 서로 다른 block `move` 10개, 서로 다른 block `delete` 10개, `BLOCK_CREATE` 10개, `create 5개 + replace 5개` mixed 요청 10개를 같은 `documentVersion`으로 동시에 보내는 경우를 포함한다.
- 검증은 각 시나리오에서 성공 개수/충돌 개수와 최종 DB 상태가 정책대로 유지되는지에 집중했고, 대표적으로 "오직 하나만 commit", "나머지는 `409` 또는 delete 경합 시 `404/409`", "최종 block/document 상태는 정확히 한 요청만 반영"을 확인했다.
- 검증은 `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionConcurrencyIntegrationTest'`로 확인했다.

## Step 21. transaction 동시성 응답 계약과 retry/no-op 검증 추가

- 경쟁형 same-block replace 테스트에 성공 응답 `documentVersion=1`, 실패 응답 `code=9005` 검증을 추가해 동시성 상황에서도 API 응답 계약이 유지되는지 확인했다.
- same-block replace 경쟁에서 충돌 후 최신 block version과 최신 `documentVersion`으로 다시 요청하면 성공하는 retry 시나리오를 추가했다.
- same-content replace 10건, same-position move 10건 no-op 경쟁 시나리오를 추가해 모든 요청이 `200`, operation `status=NO_OP`, block version / `documentVersion` 미증가로 유지되는지 확인했다.

## Step 22. documentVersion 선검증 제거

- transaction top-level `documentVersion` 필드와 응답 `documentVersion` 필드는 유지하되, 시작 시 현재 문서 version과 같다고 선검증하던 로직만 제거했다.
- `DocumentTransactionServiceImpl`은 문서 활성 상태만 확인하고, 동시성 검사는 각 block operation의 `version`으로만 처리한다.
- batch 안에 실제 editor 변경이 있으면 `Document.version`은 계속 증가시키고, 응답에도 최신 `documentVersion`을 내려주도록 유지했다.
- 서로 다른 block 대상 동시 transaction은 더 이상 문서 단위 충돌로 막지 않고, 각 block version 충돌이 없는 한 함께 성공하도록 테스트를 다시 고정했다.
- 검증은 `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionConcurrencyIntegrationTest'`로 확인했다.

## Step 23. 동시성 통합 테스트 직렬화 기준을 apply 단위로 통일

- `DocumentTransactionConcurrencyIntegrationTest`의 결정적 경합 헬퍼를 `blockService` 개별 메서드가 아니라 `DocumentTransactionServiceImpl.apply(...)` 단위 하나로 통일했다.
- mixed/batch 요청에서 latch가 "요청 수"가 아니라 "내부 service method invocation 수"를 세던 취약점을 없애고, 실제 API 1회 호출 기준 transaction 전체 순서를 검증하도록 정리했다.
- 더 이상 쓰지 않는 `blockService` spy와 `serializeConcurrentUpdates/Moves/Deletes/...` 헬퍼를 제거하고, 모든 결정적 동시성 테스트가 `serializeConcurrentTransactions(...)`만 사용하도록 단순화했다.
- 검증은 `:documents-boot:test --tests 'com.documents.api.document.DocumentTransactionConcurrencyIntegrationTest'`로 확인한다.

## Step 24. transaction request에서 documentVersion 제거

- transaction request top-level에서는 더 이상 `documentVersion`을 받지 않고, `clientId`, `batchId`, `operations`만 사용하도록 DTO와 command 매핑을 정리했다.
- 응답의 `documentVersion`과 성공 시 `Document.version` 증가 로직은 그대로 유지해, 서버가 확정한 최신 문서 snapshot만 클라이언트에 내려주도록 유지했다.
- WebMvc, service, boot 통합 테스트의 request JSON과 helper 시그니처에서 `documentVersion` 입력을 제거하고, 관련 validation 테스트도 삭제했다.
- 요구사항, save model explainer, frontend guide를 같은 계약으로 갱신했다.
