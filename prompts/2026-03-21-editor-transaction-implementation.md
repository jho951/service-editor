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
