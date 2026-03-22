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
